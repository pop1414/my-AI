package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentListItemResult;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;
import io.github.spike.myai.ingest.application.usecase.ListDocumentsUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentListFilter;
import io.github.spike.myai.ingest.domain.model.DocumentListItem;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentListRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 文档列表查询应用服务。
 *
 * <p>该服务实现 {@link ListDocumentsUseCase} 用例接口，负责：
 * <ol>
 *   <li>将应用层 Query 对象转换为领域层的 {@link DocumentListFilter} 过滤条件；</li>
 *   <li>通过授权感知的拉取策略，在全量数据中逐页加载并过滤出当前用户可读的文档；</li>
 *   <li>对过滤后的结果执行内存分页（offset/limit 截取）；</li>
 *   <li>将领域层 {@link DocumentListItem} 映射为应用层
 *       {@link DocumentListItemResult}，封装 {@code failureReason}
 *       的条件返回逻辑。</li>
 * </ol>
 *
 * <p><strong>授权过滤策略：</strong>
 * 文档列表与一般 CRUD 不同——不能简单地在 SQL 层面过滤，
 * 因为授权信息分散在 {@code document_grants} 和 {@code knowledge_base_grants}
 * 两张表中，需逐条调用 {@link AuthorizationService#requireCanReadDocument}
 * 进行三级权限判定（工作区 → 文档 → 知识库）。
 * 为提高效率，先按无授权过滤全量拉取，再在内存中逐条过滤。
 *
 * <p>设计说明：该服务为只读操作，不涉及事务管理。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ListDocumentsApplicationService implements ListDocumentsUseCase {

    /**
     * 文档列表读模型仓储（只读端口），用于分页查询文档视图。
     *
     * <p>注意：此仓储返回的是读模型（视图），非领域聚合根，
     * 包含聚合统计字段（如 indexedDocumentCount 等）。
     */
    private final DocumentListRepository documentListRepository;

    /** 当前用户上下文提供器，用于获取工作区标识限定查询范围 */
    private final CurrentUserProvider currentUserProvider;

    /**
     * 授权服务，用于对列表结果执行文档级可读过滤。
     *
     * <p>由于授权信息不在文档主表中，需在应用层逐条调用
     * {@code requireCanReadDocument} 进行权限判定。
     */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * @param documentListRepository 文档列表读模型仓储（领域端口）
     * @param currentUserProvider    当前用户上下文提供器（应用层端口）
     * @param authorizationService   授权服务（应用层）
     */
    public ListDocumentsApplicationService(
            DocumentListRepository documentListRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentListRepository = documentListRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 处理文档列表分页查询。
     *
     * <p>执行流程：
     * <ol>
     *   <li>将 Query 对象的规范化字段组装为领域过滤条件；</li>
     *   <li>调用仓储执行分页查询；</li>
     *   <li>将领域视图项流式映射为应用层结果对象；</li>
     *   <li>封装并返回分页结果。</li>
     * </ol>
     *
     * @param query 文档列表查询条件（已通过紧凑构造器校验）
     * @return 分页查询结果
     */
    @Override
    public DocumentListPageResult handle(ListDocumentsQuery query) {
        // 获取当前登录用户，限定查询范围为该用户所在工作区
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        // 构建基础过滤条件（不含 offset，由内部拉取循环控制）
        DocumentListFilter baseFilter = new DocumentListFilter(
                currentUser.workspaceId(),
                query.normalizedKbId(),
                query.requestedStatus(),
                query.normalizedFilename(),
                query.excludeDeletedByDefault(),
                query.limit(),
                0);

        // 文档列表需要按授权后的可见结果做分页——
        // 先逐页拉取全量数据，在内存中按文档级权限逐条过滤，再对过滤后的结果做 offset/limit 截取
        List<DocumentListItem> authorizedItems = loadAuthorizedItems(currentUser, baseFilter);
        // 对授权过滤后的列表执行内存分页：计算起止索引（防止越界）
        int fromIndex = Math.min(query.offset(), authorizedItems.size());
        int toIndex = Math.min(fromIndex + query.limit(), authorizedItems.size());

        // 截取当前页的数据切片，映射为应用层 DTO 并返回分页结果
        return new DocumentListPageResult(
                authorizedItems.subList(fromIndex, toIndex).stream()
                        .map(ListDocumentsApplicationService::toResult)
                        .toList(),
                authorizedItems.size(),   // total: 授权过滤后的总条目数（非数据库总条数）
                query.limit(),
                query.offset());
    }

    /**
     * 逐页拉取全量文档并过滤出当前用户可读的条目。
     *
     * <p>策略：按 limit 大小逐页从数据库拉取（避免一次性加载所有数据），
     * 对每页的条目调用 {@link #canReadDocument} 进行权限判定，
     * 仅保留有读取权限的条目。循环直到所有页遍历完毕。
     *
     * @param currentUser 当前登录用户
     * @param baseFilter  基础过滤条件（不含 offset）
     * @return 经过授权过滤后的文档列表
     */
    private List<DocumentListItem> loadAuthorizedItems(CurrentUser currentUser, DocumentListFilter baseFilter) {
        List<DocumentListItem> authorizedItems = new ArrayList<>();
        int offset = 0;
        long total = Long.MAX_VALUE;  // 初始设为最大值，首次进入循环后由 page.total() 更新

        // 逐页拉取直到覆盖所有数据
        while (offset < total) {
            // 构造当前页的过滤条件（唯一变化的是 offset）
            DocumentListPage page = documentListRepository.findPage(new DocumentListFilter(
                    baseFilter.workspaceId(),
                    baseFilter.kbId(),
                    baseFilter.status(),
                    baseFilter.filename(),
                    baseFilter.excludeDeleted(),
                    baseFilter.limit(),
                    offset));
            total = page.total();
            // 当前页无数据，停止拉取
            if (page.items().isEmpty()) {
                break;
            }
            // 对当前页逐条进行权限过滤，仅保留可读文档
            authorizedItems.addAll(page.items().stream()
                    .filter(item -> canReadDocument(currentUser, item))
                    .toList());
            // 偏移量递增，准备拉取下一页
            offset += page.limit();
        }
        return authorizedItems;
    }

    /**
     * 判断当前用户是否可读取指定文档。
     *
     * <p>通过 {@link AuthorizationService#requireCanReadDocument} 的
     * "抛异常即无权限" 语义进行判定——不抛异常表示可读，
     * 抛出 {@link AccessDeniedException} 表示无权限。
     *
     * @param currentUser 当前登录用户
     * @param item        文档列表视图项
     * @return {@code true} 可读取，{@code false} 无权限
     */
    private boolean canReadDocument(CurrentUser currentUser, DocumentListItem item) {
        try {
            // 委托授权服务执行三级权限判定（工作区 → 文档 → 知识库回退）
            authorizationService.requireCanReadDocument(currentUser, item.documentId().value(), item.kbId());
            return true;
        } catch (AccessDeniedException ex) {
            // 权限不足：静默过滤，不中断列表查询
            return false;
        }
    }

    /**
     * 将领域层列表项映射为应用层结果。
     *
     * <p>映射要点：
     * <ul>
     *   <li>{@code documentId} 提取 {@code value()} 字符串；</li>
     *   <li>{@code status} 通过 {@code name()} 转为字符串；</li>
     *   <li>{@code failureReason} 仅在状态为 {@code FAILED} 时返回，
     *       其他状态返回 {@code null}，避免前端展示无意义信息。</li>
     * </ul>
     *
     * @param item 领域层文档列表视图项
     * @return 应用层文档列表项结果
     */
    private static DocumentListItemResult toResult(DocumentListItem item) {
        return new DocumentListItemResult(
                item.documentId().value(),
                item.kbId(),
                item.filename(),
                item.fileSize(),
                item.status().name(),                                       // 枚举 → 字符串
                item.status() == UploadStatus.FAILED
                        ? item.failureReason()                              // 仅失败状态返回原因
                        : null,                                              // 其他状态返回 null
                item.createdAt(),
                item.updatedAt());
    }
}
