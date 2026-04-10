package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunkPreviewRepository;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * GetDocumentChunksPreviewApplicationService 单元测试。
 */
class GetDocumentChunksPreviewApplicationServiceTest {

    @Test
    @DisplayName("文档存在时，应返回截断后的分块预览")
    void handle_shouldReturnPreview_whenDocumentExists() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentChunkPreviewRepository chunkPreviewRepository = Mockito.mock(DocumentChunkPreviewRepository.class);
        GetDocumentChunksPreviewApplicationService service =
                new GetDocumentChunksPreviewApplicationService(documentRepository, chunkPreviewRepository);

        DocumentId documentId = new DocumentId("doc-500");
        Document doc = new Document(
                documentId,
                "kb-1",
                "hash-500",
                "sample.txt",
                200L,
                UploadStatus.INDEXED,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                Instant.now(),
                Instant.now());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(chunkPreviewRepository.countByDocumentId(eq(documentId), eq("v1"))).thenReturn(1);
        when(chunkPreviewRepository.findByDocumentId(eq(documentId), eq("v1"), eq(10), eq(0)))
                .thenReturn(List.of(new DocumentChunkPreview(
                        0,
                        "0123456789abcdefghijklmnopqrstuvwxyz",
                        36,
                        "sample.txt",
                        "hash-c-1",
                        "v1",
                        "{\"heading\":\"Intro\"}")));

        DocumentChunksPreviewResult result =
                service.handle(new GetDocumentChunksPreviewQuery("doc-500", 10, 0, 20));

        assertEquals("doc-500", result.documentId().value());
        assertEquals(1, result.chunkCount());
        assertEquals(1, result.totalChunks());
        assertEquals("0123456789abcdefghij...", result.chunks().getFirst().contentPreview());
    }

    @Test
    @DisplayName("文档不存在时，应抛出 DocumentNotFoundException")
    void handle_shouldThrowException_whenDocumentMissing() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentChunkPreviewRepository chunkPreviewRepository = Mockito.mock(DocumentChunkPreviewRepository.class);
        GetDocumentChunksPreviewApplicationService service =
                new GetDocumentChunksPreviewApplicationService(documentRepository, chunkPreviewRepository);
        when(documentRepository.findById(new DocumentId("doc-missing"))).thenReturn(Optional.empty());

        assertThrows(
                DocumentNotFoundException.class,
                () -> service.handle(new GetDocumentChunksPreviewQuery("doc-missing", 10, 0, 100)));
    }
}
