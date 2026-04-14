package io.github.spike.myai.ingest.application.service;

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
 * 文档资产删除应用服务。
 *
 * <p>该服务负责协调文档在数据库、源文件存储以及矢量索引库中的彻底删除。
 * 采用状态机思想，确保在并发场景下删除操作的安全性和幂等性。
 */
@Service
public class DeleteDocumentApplicationService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentApplicationService.class);

    private final DocumentRepository documentRepository; // 文档元数据仓库
    private final DocumentSourceStorage documentSourceStorage; // 源文件存储系统
    private final DocumentVectorIndexer documentVectorIndexer; // 矢量索引库
    private final IngestMetrics ingestMetrics; // 业务指标监控

    public DeleteDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            DocumentVectorIndexer documentVectorIndexer,
            IngestMetrics ingestMetrics) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.documentVectorIndexer = documentVectorIndexer;
        this.ingestMetrics = ingestMetrics;
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
        DocumentId documentId = new DocumentId(command.documentId());
        // 1. 查询文档，确保文档存在
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
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
        boolean markedDeleting = documentRepository.markDeleting(documentId, status, now);
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
            boolean markedDeleted = documentRepository.markDeleted(documentId, Instant.now());
            if (!markedDeleted) {
                // 此时通常是因为极其严重的并发冲突或数据库异常
                throw new IllegalStateException("mark deleted failed by CAS");
            }
            ingestMetrics.incrementDeleteSuccess();
            log.info("Document deleted. documentId={}", documentId.value());
        } catch (Exception ex) {
            // 7. 异常回滚：如果物理清理失败，尝试将状态回滚到删除前的原始状态，以便用户重试
            boolean rollback = documentRepository.rollbackDeleting(documentId, status, Instant.now());
            if (!rollback) {
                log.warn("Rollback deleting state failed by CAS. documentId={}", documentId.value());
            }
            log.error("Document delete failed. documentId={}", documentId.value(), ex);
            throw new DocumentDeleteFailedException("failed to delete document asset", ex);
        }
    }
}
