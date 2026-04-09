package io.github.spike.myai.ingest.application.service;

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
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档重处理应用服务。
 */
@Service
public class ReprocessDocumentApplicationService implements ReprocessDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReprocessDocumentApplicationService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVectorIndexer documentVectorIndexer;

    public ReprocessDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentVectorIndexer documentVectorIndexer) {
        this.documentRepository = documentRepository;
        this.documentVectorIndexer = documentVectorIndexer;
    }

    @Override
    public DocumentStatusResult handle(ReprocessDocumentCommand command) {
        DocumentId documentId = new DocumentId(command.documentId());
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        // INGESTING 阶段禁止重处理，避免与正在进行的分块/向量化冲突。
        if (document.status() == UploadStatus.INGESTING) {
            throw new IllegalStateException("document is ingesting and cannot be reprocessed");
        }
        // 仅允许 FAILED/INDEXED 进入重处理，避免跳过必要状态。
        if (document.status() != UploadStatus.FAILED && document.status() != UploadStatus.INDEXED) {
            throw new IllegalStateException("document status not allowed for reprocess: " + document.status());
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
        boolean updated = documentRepository.requestReprocess(documentId, document.status(), newSplitVersion, now);
        if (!updated) {
            throw new IllegalStateException("document status changed, reprocess aborted");
        }


        try {
            // 清理旧版本向量，避免历史向量污染检索结果。
            documentVectorIndexer.deleteByDocumentIdAndSplitVersion(documentId, oldSplitVersion);
        } catch (Exception ex) {
            // 清理失败时回退到 FAILED，并记录错误，防止文档“消失”。
            String reason = trimFailureReason(ex.getMessage());
            documentRepository.markFailed(
                    documentId,
                    UploadStatus.UPLOADED,
                    reason,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    now,
                    now);
            log.warn("Reprocess cleanup failed. documentId={}, reason={}", documentId.value(), reason, ex);
            throw new IllegalStateException("reprocess cleanup failed");
        }

        log.info(
                "Reprocess requested. documentId={}, oldSplitVersion={}, newSplitVersion={}",
                documentId.value(),
                oldSplitVersion,
                newSplitVersion);
        return new DocumentStatusResult(documentId, UploadStatus.UPLOADED);
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
