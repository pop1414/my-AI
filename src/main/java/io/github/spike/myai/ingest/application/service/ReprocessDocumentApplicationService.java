package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.ReprocessDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.SplitVersion;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.github.spike.myai.shared.rest.BusinessException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 文档重处理应用服务（Application Service）。
 *
 * <p>该服务实现 {@link ReprocessDocumentUseCase} 用例接口，
 * 负责对处于 FAILED 或 INDEXED 状态的文档发起全量重处理。
 *
 * <h3>核心机制：版本化重处理</h3>
 * <p>通过 {@code splitVersion} 递增实现版本隔离：
 * <ol>
 *   <li><b>避免冲突</b>：异步删除旧版本向量时，利用版本号过滤裁剪，
 *       不会误删新开始处理的向量数据；</li>
 *   <li><b>检索隔离</b>：检索接口通过版本过滤，确保用户查不到
 *       正在重洗的中间状态数据。</li>
 * </ol>
 *
 * <h3>约束</h3>
 * <ul>
 *   <li>INGESTING 状态禁止重处理，避免与正在进行的分块/向量化冲突；</li>
 *   <li>仅 FAILED / INDEXED 可进入重处理；</li>
 *   <li>清理旧版本向量失败时回退到 FAILED 状态。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ReprocessDocumentApplicationService implements ReprocessDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReprocessDocumentApplicationService.class);

    /** 文档仓储端口：用于查询文档状态及执行 CAS 状态更新 */
    private final DocumentRepository documentRepository;

    /** 矢量索引端口：用于删除旧版本向量数据，避免历史向量污染检索结果 */
    private final DocumentVectorIndexer documentVectorIndexer;

    /** 当前用户上下文提供器：用于获取工作区标识 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权服务：用于校验当前用户是否具备知识库贡献权限 */
    private final AuthorizationService authorizationService;

    /** 审计事件仓储：记录重处理治理动作成功与失败上下文 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入。
     *
     * @param documentRepository     文档仓储（领域端口）
     * @param documentVectorIndexer  矢量索引器（领域端口）
     * @param currentUserProvider    当前用户上下文提供器（应用层端口）
     * @param authorizationService   授权服务（应用层）
     * @param auditEventRepository   审计事件仓储
     */
    public ReprocessDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentVectorIndexer documentVectorIndexer,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService,
            AuditEventRepository auditEventRepository) {
        this.documentRepository = documentRepository;
        this.documentVectorIndexer = documentVectorIndexer;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public DocumentStatusResult handle(ReprocessDocumentCommand command) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String workspaceId = currentUser.workspaceId();
        DocumentId documentId = new DocumentId(command.documentId());
        Document document = documentRepository.findById(workspaceId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        try {
            authorizationService.requireCanContributeKnowledgeBase(currentUser, document.kbId());
        } catch (AccessDeniedException ex) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.FORBIDDEN,
                    "DOCUMENT_REPROCESS_NO_PERMISSION",
                    "你没有重处理该文档的权限，请联系管理员");
        }
        if (isExpectedLatestVersionStale(command.expectedLatestVersionNumber(), document)) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STALE_LATEST_VERSION",
                    "当前最新版本已变化，请刷新详情后重试");
        }
        // INGESTING 阶段禁止重处理，避免与正在进行的分块/向量化冲突。
        if (document.status() == UploadStatus.INGESTING) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STATE_CHANGED",
                    "当前文档状态已变化，请刷新详情后重试");
        }
        // 仅允许 FAILED/INDEXED 进入重处理，避免跳过必要状态。
        if (document.status() != UploadStatus.FAILED && document.status() != UploadStatus.INDEXED) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.CONFLICT,
                    "DOCUMENT_REPROCESS_NOT_ALLOWED_STATUS",
                    "重处理仅允许在当前最新版本为 INDEXED 或 FAILED 时发起");
        }

        // splitVersion 递增，用于区分新旧向量版本，确保删旧不误删新。
        // 【核心设计说明】：
        // 这里递增 splitVersion 是为了实现“版本化重处理”。
        // 1. 避免冲突：异步删除旧版本向量时，利用版本号过滤裁剪，不会误删新开始处理的向量数据。
        // 2. 检索隔离：后续检索接口可以通过 version 过滤，确保用户查不到正在重洗的中间状态数据。
        String oldSplitVersion = document.splitVersion();
        String newSplitVersion = SplitVersion.next(oldSplitVersion);
        Instant now = Instant.now();

        // CAS 更新：仅当当前状态仍为预期状态时才允许进入重处理队列。
        boolean updated = documentRepository.requestReprocess(
                workspaceId,
                documentId,
                document.status(),
                newSplitVersion,
                now);
        if (!updated) {
            throw latestConflict(currentUser, workspaceId, documentId, document, command.expectedLatestVersionNumber());
        }

        try {
            // 清理旧版本向量，避免历史向量污染检索结果。
            documentVectorIndexer.deleteByDocumentIdAndSplitVersion(documentId, oldSplitVersion);
        } catch (Exception ex) {
            // 清理失败时回退到 FAILED，并记录错误，防止文档"消失"。
            // 重处理清理阶段尚未产出 processing_metadata，传入 null 表示无元数据可回填。
            String reason = trimFailureReason(ex.getMessage());
            documentRepository.markFailed(
                    workspaceId,
                    documentId,
                    UploadStatus.UPLOADED,
                    reason,
                    null,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    now,
                    now);
            log.warn("Reprocess cleanup failed. documentId={}, reason={}", documentId.value(), reason, ex);
            auditFailure(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    "DOCUMENT_REPROCESS_CLEANUP_FAILED",
                    "reprocess cleanup failed");
            throw new IllegalStateException("reprocess cleanup failed");
        }

        log.info(
                "Reprocess requested. documentId={}, oldSplitVersion={}, newSplitVersion={}",
                documentId.value(),
                oldSplitVersion,
                newSplitVersion);
        IngestAuditEventRecorder.save(auditEventRepository, IngestAuditEvents.documentGovernanceSucceeded(
                currentUser,
                IngestAuditEvents.DOCUMENT_REPROCESS_REQUESTED,
                documentId,
                document.kbId(),
                document.latestVersionNumber(),
                command.expectedLatestVersionNumber(),
                "REQUESTED",
                Instant.now()));
        // 重处理仅将文档回退到 UPLOADED 等待重新调度，此时所有旧元数据已清除，故 processingMetadata 传 null。
        return new DocumentStatusResult(
                documentId,
                document.latestVersionNumber(),
                document.filename(),
                document.latestVersionOriginType(),
                UploadStatus.UPLOADED,
                null);
    }

    /**
     * 判断请求携带的 latest 版本号是否已过期。
     *
     * @param expectedLatestVersionNumber 调用方页面看到的 latest 版本号，可为空
     * @param document 当前文档快照
     * @return 是否过期
     */
    private static boolean isExpectedLatestVersionStale(Integer expectedLatestVersionNumber, Document document) {
        return expectedLatestVersionNumber != null
                && expectedLatestVersionNumber != document.latestVersionNumber();
    }

    /**
     * CAS 失败后重读文档，区分页面过期和状态变化。
     *
     * @param currentUser 当前用户
     * @param workspaceId 工作区标识
     * @param documentId 文档资产 ID
     * @param originalDocument 初始读取的文档快照
     * @param expectedLatestVersionNumber 调用方页面看到的 latest 版本号，可为空
     * @return 业务异常
     */
    private BusinessException latestConflict(
            CurrentUser currentUser,
            String workspaceId,
            DocumentId documentId,
            Document originalDocument,
            Integer expectedLatestVersionNumber) {
        Document observedDocument = documentRepository.findById(workspaceId, documentId).orElse(originalDocument);
        if (isExpectedLatestVersionStale(expectedLatestVersionNumber, observedDocument)) {
            return auditAndBusiness(
                    currentUser,
                    observedDocument,
                    expectedLatestVersionNumber,
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STALE_LATEST_VERSION",
                    "当前最新版本已变化，请刷新详情后重试");
        }
        return auditAndBusiness(
                currentUser,
                observedDocument,
                expectedLatestVersionNumber,
                HttpStatus.CONFLICT,
                "VERSION_CONFLICT_STATE_CHANGED",
                "当前文档状态已变化，请刷新详情后重试");
    }

    /**
     * 写入重处理失败审计并返回业务异常。
     *
     * @param currentUser 当前用户
     * @param document 文档快照
     * @param expectedLatestVersionNumber 调用方页面看到的 latest 版本号，可为空
     * @param status HTTP 状态码
     * @param code 业务错误码
     * @param message 错误消息
     * @return 业务异常
     */
    private BusinessException auditAndBusiness(
            CurrentUser currentUser,
            Document document,
            Integer expectedLatestVersionNumber,
            HttpStatus status,
            String code,
            String message) {
        auditFailure(currentUser, document, expectedLatestVersionNumber, code, message);
        return new BusinessException(status, code, message);
    }

    /**
     * 写入重处理失败审计。
     *
     * @param currentUser 当前用户
     * @param document 文档快照
     * @param expectedLatestVersionNumber 调用方页面看到的 latest 版本号，可为空
     * @param errorCode 业务错误码
     * @param errorMessage 服务端错误消息
     */
    private void auditFailure(
            CurrentUser currentUser,
            Document document,
            Integer expectedLatestVersionNumber,
            String errorCode,
            String errorMessage) {
        IngestAuditEventRecorder.save(auditEventRepository, IngestAuditEvents.documentGovernanceFailed(
                currentUser,
                IngestAuditEvents.DOCUMENT_REPROCESS_REQUESTED,
                document.documentId(),
                document.kbId(),
                document.latestVersionNumber(),
                expectedLatestVersionNumber,
                "FAILED",
                errorCode,
                errorMessage,
                Instant.now()));
    }

    private static String trimFailureReason(String reason) {
        // 失败原因兜底与截断，避免写入过长文本。
        if (reason == null || reason.isBlank()) {
            return "unknown reprocess error";
        }
        int limit = 500;
        if (reason.length() <= limit) {
            return reason;
        }
        return reason.substring(0, limit);
    }
}
