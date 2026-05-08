package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentListItemResult;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;
import io.github.spike.myai.ingest.application.usecase.ListDocumentsUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentListFilter;
import io.github.spike.myai.ingest.domain.model.DocumentListItem;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentListRepository;
import org.springframework.stereotype.Service;

/**
 * 文档列表查询应用服务。
 *
 * <p>该服务实现 {@link ListDocumentsUseCase} 用例接口，负责：
 * <ol>
 *   <li>将应用层 Query 对象转换为领域层的 {@link DocumentListFilter} 过滤条件；</li>
 *   <li>调用读模型仓储 {@link DocumentListRepository} 执行分页查询；</li>
 *   <li>将领域层返回的 {@link DocumentListItem} 映射为应用层
 *       {@link DocumentListItemResult}，并封装 {@code failureReason}
 *       的条件返回逻辑。</li>
 * </ol>
 *
 * <p>设计说明：该服务为只读操作，不涉及事务管理。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ListDocumentsApplicationService implements ListDocumentsUseCase {

    /** 文档列表读模型仓储（只读），用于分页查询文档视图 */
    private final DocumentListRepository documentListRepository;

    /**
     * 构造器注入。
     *
     * @param documentListRepository 文档列表仓储
     */
    public ListDocumentsApplicationService(DocumentListRepository documentListRepository) {
        this.documentListRepository = documentListRepository;
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
        // 1. 将应用层 Query 转换为领域层过滤条件
        //    excludeDeletedByDefault() 实现默认不展示已删除文档的语义
        DocumentListPage page = documentListRepository.findPage(new DocumentListFilter(
                query.normalizedKbId(),          // 知识库 ID（null 表示不过滤）
                query.requestedStatus(),          // 状态过滤（null 表示不过滤）
                query.normalizedFilename(),       // 文件名模糊匹配（null 表示不过滤）
                query.excludeDeletedByDefault(),  // 是否排除已删除文档
                query.limit(),                    // 每页条数
                query.offset()));                 // 偏移量

        // 2. 将领域视图项映射为应用层结果，收集为不可变列表
        return new DocumentListPageResult(
                page.items().stream()
                        .map(ListDocumentsApplicationService::toResult)
                        .toList(),
                page.total(),
                page.limit(),
                page.offset());
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
