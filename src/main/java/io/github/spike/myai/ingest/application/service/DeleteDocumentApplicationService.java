package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
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
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>冲突检查：INGESTING/DELETING 状态拒绝删除；</li>
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

    /**
     * 构造器注入。
     *
     * @param documentRepository     文档元数据仓储（领域端口）
     * @param documentSourceStorage  源文件存储（领域端口）
     * @param documentVectorIndexer  矢量索引器（领域端口）
     * @param ingestMetrics          业务指标监控（应用层）
     * @param currentUserProvider    当前用户上下文提供器（应用层端口）
     * @param authorizationService   授权服务（应用层）
     */
    public DeleteDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            DocumentVectorIndexer documentVectorIndexer,
            IngestMetrics ingestMetrics,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.documentVectorIndexer = documentVectorIndexer;
        this.ingestMetrics = ingestMetrics;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 执行文档删除动作。
     *
     * @param command 包含待删除文档 ID 的命令对象
     * @throws DocumentNotFoundException 文档不存在
     * @throws DocumentDeleteConflictException 状态冲突（如文档正在处理中）
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
        authorizationService.requireCanManageDocument(currentUser, documentId.value(), document.kbId());
        UploadStatus status = document.status();

        // 2. 幂等检查：如果文档已经是 DELETED 状态，视为成功
        if (status == UploadStatus.DELETED) {
            ingestMetrics.incrementDeleteSuccess();
            return;
        }

        // 3. 冲突检查：如果文档正在处理中（INGESTING）或已在执行删除（DELETING），拒绝本次请求
        if (status == UploadStatus.INGESTING || status == UploadStatus.DELETING) {
            ingestMetrics.incrementDeleteConflict();
            log.warn("Delete rejected by conflict status. documentId={}, status={}", documentId.value(), status);
            throw new DocumentDeleteConflictException("document is in conflict status: " + status);
        }

        Instant now = Instant.now();
        // 4. 锁定状态：通过 CAS (Compare and Swap) 将状态原子性地变更为 DELETING
        boolean markedDeleting = documentRepository.markDeleting(workspaceId, documentId, status, now);
        if (!markedDeleting) {
            // 如果标记失败，说明在查询和标记之间状态由于并发操作发生了变化
            ingestMetrics.incrementDeleteConflict();
            log.warn("Delete rejected by CAS conflict. documentId={}, status={}", documentId.value(), status);
            throw new DocumentDeleteConflictException("document status changed, delete aborted");
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
            log.info("Document deleted. documentId={}", documentId.value());
        } catch (Exception ex) {
            // 7. 异常回滚：如果物理清理失败，尝试将状态回滚到删除前的原始状态，以便用户重试
            boolean rollback = documentRepository.rollbackDeleting(workspaceId, documentId, status, Instant.now());
            if (!rollback) {
                log.warn("Rollback deleting state failed by CAS. documentId={}", documentId.value());
            }
            log.error("Document delete failed. documentId={}", documentId.value(), ex);
            throw new DocumentDeleteFailedException("failed to delete document asset", ex);
        }
    }
}
