package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
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
 */
@Service
public class DeleteDocumentApplicationService implements DeleteDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteDocumentApplicationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentSourceStorage documentSourceStorage;
    private final DocumentVectorIndexer documentVectorIndexer;

    public DeleteDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            DocumentVectorIndexer documentVectorIndexer) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.documentVectorIndexer = documentVectorIndexer;
    }

    @Override
    public void handle(DeleteDocumentCommand command) {
        DocumentId documentId = new DocumentId(command.documentId());
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        UploadStatus status = document.status();

        if (status == UploadStatus.DELETED) {
            // 幂等删除：已删除则直接返回成功。
            return;
        }
        if (status == UploadStatus.INGESTING || status == UploadStatus.DELETING) {
            throw new DocumentDeleteConflictException("document is in conflict status: " + status);
        }

        Instant now = Instant.now();
        boolean markedDeleting = documentRepository.markDeleting(documentId, status, now);
        if (!markedDeleting) {
            throw new DocumentDeleteConflictException("document status changed, delete aborted");
        }

        try {
            documentSourceStorage.deleteByDocumentId(documentId);
            documentVectorIndexer.deleteByDocumentId(documentId);
            boolean markedDeleted = documentRepository.markDeleted(documentId, Instant.now());
            if (!markedDeleted) {
                throw new IllegalStateException("mark deleted failed by CAS");
            }
            log.info("Document deleted. documentId={}", documentId.value());
        } catch (Exception ex) {
            boolean rollback = documentRepository.rollbackDeleting(documentId, status, Instant.now());
            if (!rollback) {
                log.warn("Rollback deleting state failed by CAS. documentId={}", documentId.value());
            }
            log.error("Document delete failed. documentId={}", documentId.value(), ex);
            throw new DocumentDeleteFailedException("failed to delete document asset", ex);
        }
    }
}
