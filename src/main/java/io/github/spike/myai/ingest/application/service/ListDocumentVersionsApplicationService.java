package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.ListDocumentVersionsQuery;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryItemResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryResult;
import io.github.spike.myai.ingest.application.usecase.ListDocumentVersionsUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistoryItem;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentVersionHistoryRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 文档版本历史查询应用服务。
 *
 * <p>负责编排"查询文档版本历史"用例的完整事务流程：
 * <ol>
 *   <li>获取当前用户身份；</li>
 *   <li>校验目标文档是否存在；</li>
 *   <li>校验当前用户对该文档的读取权限；</li>
 *   <li>调取版本历史数据并按版本号倒序组装返回结果。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ListDocumentVersionsApplicationService implements ListDocumentVersionsUseCase {

    /**
     * 版本排序契约常量：按版本号降序排列。
     * 前端和 API 文档依赖此固定值来理解返回数据的顺序语义。
     */
    static final String SORT_VERSION_NUMBER_DESC = "versionNumber,DESC";

    /** 文档仓储：按工作区和 ID 查询文档主记录，校验文档是否存在 */
    private final DocumentRepository documentRepository;
    /** 版本历史只读仓储：按文档 ID 查询所有版本记录 */
    private final DocumentVersionHistoryRepository documentVersionHistoryRepository;
    /** 当前用户提供器：获取请求上下文中的用户信息 */
    private final CurrentUserProvider currentUserProvider;
    /** 授权服务：校验当前用户对目标文档的读取权限 */
    private final AuthorizationService authorizationService;

    /**
     * 构造函数（Spring 构造器注入）。
     *
     * <p>所有依赖均为不可变注入，保证线程安全，
     * 同时便于单元测试时注入 Mock 实现。
     *
     * @param documentRepository              文档主表仓储
     * @param documentVersionHistoryRepository 版本历史只读仓储
     * @param currentUserProvider             当前用户提供器
     * @param authorizationService            授权服务
     */
    public ListDocumentVersionsApplicationService(
            DocumentRepository documentRepository,
            DocumentVersionHistoryRepository documentVersionHistoryRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.documentVersionHistoryRepository = documentVersionHistoryRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 处理文档版本历史查询请求。
     *
     * <p>执行流程：
     * <ol>
     *   <li><b>获取用户</b> —— 从安全上下文中提取当前登录用户，
     *       若未认证则直接抛出异常；</li>
     *   <li><b>文档定位</b> —— 将标准化后的 documentId 转为领域标识，
     *       在当前用户工作区内查找文档主记录，不存在则抛出
     *       {@link DocumentNotFoundException}；</li>
     *   <li><b>权限校验</b> —— 调用授权服务确认当前用户对该文档具有读取权限；</li>
     *   <li><b>数据查询</b> —— 从版本历史仓储中拉取该文档的全量版本记录
     *       （按版本号降序排列）；</li>
     *   <li><b>结果组装</b> —— 逐条映射领域模型为应用层返回结果，
     *       并计算 isLatestVersion / isAskableVersion 等衍生标记。</li>
     * </ol>
     *
     * @param query 包含目标文档 ID 的查询对象
     * @return 文档版本历史聚合结果（含排序契约与版本列表）
     * @throws DocumentNotFoundException 当目标文档在当前工作区不存在时
     */
    @Override
    public DocumentVersionHistoryResult handle(ListDocumentVersionsQuery query) {
        // 步骤 1：获取当前登录用户（未认证时由 Provider 抛出异常）
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();

        // 步骤 2：将标准化 ID 转为强类型领域标识
        DocumentId documentId = new DocumentId(query.normalizedDocumentId());

        // 步骤 3：在工作区范围内查找文档，不存在则快速失败
        Document document = documentRepository.findById(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));

        // 步骤 4：校验用户对该文档的读取权限（无权限时内部抛出异常）
        authorizationService.requireCanReadDocument(currentUser, documentId.value(), document.kbId());

        // 步骤 5：查询版本历史 → 推导问答基线 → 领域模型映射为应用层结果 → 组装返回
        List<DocumentVersionHistoryItem> historyItems =
                documentVersionHistoryRepository.findByDocumentIdOrderByVersionNumberDesc(
                        currentUser.workspaceId(),
                        documentId);
        int askableVersionNumber = resolveAskableVersionNumber(historyItems);

        return new DocumentVersionHistoryResult(
                documentId.value(),
                SORT_VERSION_NUMBER_DESC,
                historyItems.stream()
                        .map(item -> toResult(item, askableVersionNumber))
                        .toList());
    }

    /**
     * 将领域模型 {@link DocumentVersionHistoryItem} 转换为应用层结果。
     *
     * <p>转换规则：
     * <ul>
     *   <li><b>isLatestVersion</b> —— 当 item.versionNumber 等于 latestVersionNumber 时为 true；</li>
     *   <li><b>failureReason</b> —— 仅当 status 为 FAILED 时返回原始值，
     *       其余状态返回 null（避免无关失败原因污染正常状态的版本记录）；</li>
     *   <li><b>isAskableVersion</b> —— 当该版本命中当前问答基线时为 true；
     *       当前问答基线优先使用已 INDEXED 的最新版本，否则回退到最近一个已 INDEXED 的历史版本。</li>
     * </ul>
     *
     * @param item 领域层版本历史项
     * @param askableVersionNumber 当前可问答版本号，无可问答版本时为 0
     * @return 应用层版本历史项返回结果
     */
    private static DocumentVersionHistoryItemResult toResult(DocumentVersionHistoryItem item, int askableVersionNumber) {
        // 判定当前版本是否为最新版本（通过比较 versionNumber 与 latestVersionNumber）
        boolean isLatestVersion = item.versionNumber() == item.latestVersionNumber();

        return new DocumentVersionHistoryItemResult(
                item.documentId().value(),
                item.versionNumber(),
                // 枚举值转字符串，前端按约定显示
                item.versionOriginType().name(),
                // 回滚来源版本号：非回滚版本为 null
                item.rollbackFromVersionNumber(),
                item.filename(),
                item.fileSize(),
                item.status().name(),
                // 仅失败状态返回原因，其余状态统一置 null
                item.status() == UploadStatus.FAILED ? item.failureReason() : null,
                item.createdAt(),
                item.updatedAt(),
                isLatestVersion,
                item.versionNumber() == askableVersionNumber);
    }

    private static int resolveAskableVersionNumber(List<DocumentVersionHistoryItem> historyItems) {
        return historyItems.stream()
                .filter(item -> item.status() == UploadStatus.INDEXED)
                .map(DocumentVersionHistoryItem::versionNumber)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
}
