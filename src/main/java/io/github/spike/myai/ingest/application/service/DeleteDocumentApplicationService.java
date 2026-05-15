package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.monitoring.IngestMetrics;
import io.github.spike.myai.ingest.application.usecase.DeleteDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.github.spike.myai.shared.rest.BusinessException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 文档资产删除应用服务（Application Service）。
 *
 * <p>该服务实现 {@link DeleteDocumentUseCase} 用例接口，
 * 负责协调文档在数据库、源文件存储以及矢量索引库中的彻底删除。
 * 采用<b>状态机 + CAS 乐观锁</b>确保并发场景下删除操作的安全性和幂等性。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>查询文档并校验存在性；</li>
 *   <li>幂等检查：已删除则直接返回成功；</li>
 *   <li>冲突检查：UPLOADED/INGESTING/DELETING 状态拒绝删除；</li>
 *   <li>CAS 锁定为 DELETING 状态；</li>
 *   <li>清理物理资产（源文件 + 向量索引）；</li>
 *   <li>标记为最终 DELETED 状态；</li>
 *   <li>异常回滚：物理清理失败时恢复到删除前状态。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class DeleteDocumentApplicationService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentApplicationService.class);

    /**
     * 文档元数据仓储端口：用于查询文档及执行 CAS 状态变更
     * （markDeleting → markDeleted → rollbackDeleting）。
     */
    private final DocumentRepository documentRepository;

    /** 源文件存储端口：用于物理删除上传的原始文件（如 MinIO / 本地文件系统） */
    private final DocumentSourceStorage documentSourceStorage;

    /** 矢量索引端口：用于删除文档对应的向量数据（如 pgvector / Elasticsearch） */
    private final DocumentVectorIndexer documentVectorIndexer;

    /**
     * 业务指标监控：记录删除操作的成功/冲突次数，
     * 供 Prometheus / Grafana 等监控系统采集。
     */
    private final IngestMetrics ingestMetrics;

    /** 当前用户上下文提供器：用于获取工作区标识 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权服务：用于校验当前用户是否具备文档管理权限 */
    private final AuthorizationService authorizationService;

    /** 审计事件仓储：记录删除治理动作成功与失败上下文 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入。
     *
     * @param documentRepository     文档元数据仓储（领域端口）
     * @param documentSourceStorage  源文件存储（领域端口）
     * @param documentVectorIndexer  矢量索引器（领域端口）
     * @param ingestMetrics          业务指标监控（应用层）
     * @param currentUserProvider    当前用户上下文提供器（应用层端口）
     * @param authorizationService   授权服务（应用层）
     * @param auditEventRepository   审计事件仓储
     */
    public DeleteDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            DocumentVectorIndexer documentVectorIndexer,
            IngestMetrics ingestMetrics,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService,
            AuditEventRepository auditEventRepository) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.documentVectorIndexer = documentVectorIndexer;
        this.ingestMetrics = ingestMetrics;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行文档删除动作。
     *
     * @param command 包含待删除文档 ID 的命令对象
     * @throws DocumentNotFoundException 文档不存在
     * @throws BusinessException 状态冲突或权限不足
     * @throws DocumentDeleteFailedException 其他删除异常
     */
    @Override
    public void handle(DeleteDocumentCommand command) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String workspaceId = currentUser.workspaceId();
        DocumentId documentId = new DocumentId(command.documentId());
        // 1. 查询文档，确保文档存在
        Document document = documentRepository.findById(workspaceId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        try {
            authorizationService.requireCanManageDocument(currentUser, documentId.value(), document.kbId());
        } catch (AccessDeniedException ex) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.FORBIDDEN,
                    "DOCUMENT_DELETE_NO_MANAGE_PERMISSION",
                    "你没有删除该文档的权限，请联系管理员");
        }
        UploadStatus status = document.status();

        if (isExpectedLatestVersionStale(command.expectedLatestVersionNumber(), document)) {
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STALE_LATEST_VERSION",
                    "当前最新版本已变化，请刷新详情后重试");
        }

        // 2. 幂等检查：如果文档已经是 DELETED 状态，视为成功
        if (status == UploadStatus.DELETED) {
            ingestMetrics.incrementDeleteSuccess();
            IngestAuditEventRecorder.save(auditEventRepository, IngestAuditEvents.documentGovernanceSucceeded(
                    currentUser,
                    IngestAuditEvents.DOCUMENT_DELETE_REQUESTED,
                    documentId,
                    document.kbId(),
                    document.latestVersionNumber(),
                    command.expectedLatestVersionNumber(),
                    "ALREADY_DELETED",
                    Instant.now()));
            return;
        }

        // 3. 冲突检查：文档已进入待处理、处理中或删除中时，拒绝插入新的删除执行态。
        if (isExecutionStatus(status)) {
            ingestMetrics.incrementDeleteConflict();
            log.warn("Delete rejected by conflict status. documentId={}, status={}", documentId.value(), status);
            throw auditAndBusiness(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STATE_CHANGED",
                    "当前文档状态已变化，请刷新详情后重试");
        }

        Instant now = Instant.now();
        // 4. 锁定状态：通过 CAS (Compare and Swap) 将状态原子性地变更为 DELETING
        boolean markedDeleting = documentRepository.markDeleting(workspaceId, documentId, status, now);
        if (!markedDeleting) {
            // 如果标记失败，说明在查询和标记之间状态由于并发操作发生了变化
            ingestMetrics.incrementDeleteConflict();
            log.warn("Delete rejected by CAS conflict. documentId={}, status={}", documentId.value(), status);
            throw latestConflict(currentUser, workspaceId, documentId, document, command.expectedLatestVersionNumber());
        }

        try {
            // 5. 执行物理资产清理：删除源文件和矢量索引
            documentSourceStorage.deleteByDocumentId(documentId);
            documentVectorIndexer.deleteByDocumentId(documentId);

            // 6. 最终确认：将状态更新为最终态 DELETED
            boolean markedDeleted = documentRepository.markDeleted(workspaceId, documentId, Instant.now());
            if (!markedDeleted) {
                // 此时通常是因为极其严重的并发冲突或数据库异常
                throw new IllegalStateException("mark deleted failed by CAS");
            }
            ingestMetrics.incrementDeleteSuccess();
            IngestAuditEventRecorder.save(auditEventRepository, IngestAuditEvents.documentGovernanceSucceeded(
                    currentUser,
                    IngestAuditEvents.DOCUMENT_DELETE_REQUESTED,
                    documentId,
                    document.kbId(),
                    document.latestVersionNumber(),
                    command.expectedLatestVersionNumber(),
                    "DELETED",
                    Instant.now()));
            log.info("Document deleted. documentId={}", documentId.value());
        } catch (Exception ex) {
            // 7. 异常回滚：如果物理清理失败，尝试将状态回滚到删除前的原始状态，以便用户重试
            boolean rollback = documentRepository.rollbackDeleting(workspaceId, documentId, status, Instant.now());
            if (!rollback) {
                log.warn("Rollback deleting state failed by CAS. documentId={}", documentId.value());
            }
            log.error("Document delete failed. documentId={}", documentId.value(), ex);
            auditFailure(
                    currentUser,
                    document,
                    command.expectedLatestVersionNumber(),
                    "DOCUMENT_DELETE_FAILED",
                    "failed to delete document asset");
            throw new DocumentDeleteFailedException("failed to delete document asset", ex);
        }
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
     * 写入删除失败审计并返回业务异常。
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
     * 写入删除失败审计。
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
                IngestAuditEvents.DOCUMENT_DELETE_REQUESTED,
                document.documentId(),
                document.kbId(),
                document.latestVersionNumber(),
                expectedLatestVersionNumber,
                "FAILED",
                errorCode,
                errorMessage,
                Instant.now()));
    }

    /**
     * 判断文档是否处于治理动作或处理链路的执行态。
     *
     * <p>{@link UploadStatus#UPLOADED} 可能来自首次上传、上传新版本、版本回退或重处理；
     * 这些状态均代表已有执行链路等待或正在推进，删除动作不得并发插入。
     *
     * @param status 当前文档状态
     * @return 是否处于执行态
     */
    private static boolean isExecutionStatus(UploadStatus status) {
        return status == UploadStatus.UPLOADED
                || status == UploadStatus.INGESTING
                || status == UploadStatus.DELETING;
    }
}
