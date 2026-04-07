package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.usecase.ClaimNextUploadedDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 抢占待处理文档应用服务。
 *
 * <p>该服务只做“挑选 + CAS 抢占”，不执行解析/分块/向量化。
 */
@Service
public class ClaimNextUploadedDocumentApplicationService implements ClaimNextUploadedDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClaimNextUploadedDocumentApplicationService.class);

    private final DocumentRepository documentRepository;

    public ClaimNextUploadedDocumentApplicationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public Optional<DocumentId> handle() {
        // 始终取最早的 UPLOADED，尽量保持处理顺序稳定。
        Optional<Document> candidate = documentRepository.findOldestByStatus(UploadStatus.UPLOADED);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        Document document = candidate.get();
        // CAS 抢占：只有当前状态仍为 UPLOADED 才能推进到 INGESTING。
        // 若并发下被其它 worker 先一步更新，本次抢占会返回 false。
        boolean claimed = documentRepository.compareAndSetStatus(
                document.documentId(),
                UploadStatus.UPLOADED,
                UploadStatus.INGESTING,
                null,
                Instant.now());
        if (!claimed) {
            log.debug("Skip claim because status changed concurrently. documentId={}", document.documentId().value());
            return Optional.empty();
        }
        log.info("Claimed uploaded document. documentId={}", document.documentId().value());
        return Optional.of(document.documentId());
    }
}
