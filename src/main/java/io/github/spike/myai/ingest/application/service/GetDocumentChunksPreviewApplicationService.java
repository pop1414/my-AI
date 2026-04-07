package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentChunkPreviewRepository;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 文档分块预览应用服务。
 */
@Service
public class GetDocumentChunksPreviewApplicationService implements GetDocumentChunksPreviewUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentChunkPreviewRepository documentChunkPreviewRepository;

    public GetDocumentChunksPreviewApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkPreviewRepository documentChunkPreviewRepository) {
        this.documentRepository = documentRepository;
        this.documentChunkPreviewRepository = documentChunkPreviewRepository;
    }

    @Override
    public DocumentChunksPreviewResult handle(GetDocumentChunksPreviewQuery query) {
        DocumentId documentId = new DocumentId(query.documentId());
        if (documentRepository.findById(documentId).isEmpty()) {
            throw new DocumentNotFoundException("document not found: " + documentId.value());
        }

        List<DocumentChunkPreviewItemResult> items = documentChunkPreviewRepository
                .findByDocumentId(documentId, query.limit())
                .stream()
                .map(chunk -> toItemResult(chunk, query.previewChars()))
                .toList();

        return new DocumentChunksPreviewResult(documentId, items.size(), items);
    }

    private static DocumentChunkPreviewItemResult toItemResult(DocumentChunkPreview chunk, int previewChars) {
        return new DocumentChunkPreviewItemResult(
                chunk.chunkIndex(),
                truncateForPreview(chunk.content(), previewChars),
                chunk.sourceFile(),
                chunk.contentHash(),
                chunk.splitVersion());
    }

    private static String truncateForPreview(String content, int previewChars) {
        if (content == null) {
            return "";
        }
        if (content.length() <= previewChars) {
            return content;
        }
        return content.substring(0, previewChars) + "...";
    }
}

