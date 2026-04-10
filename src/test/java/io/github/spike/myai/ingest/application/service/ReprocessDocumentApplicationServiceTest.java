package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ReprocessDocumentApplicationService 单元测试。
 */
class ReprocessDocumentApplicationServiceTest {

    @Test
    @DisplayName("INGESTING 状态触发重处理应抛出冲突异常")
    void handle_shouldThrow_whenIngesting() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(repository, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-rep-1");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-1",
                "a.txt",
                1L,
                UploadStatus.INGESTING,
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
        when(repository.findById(documentId)).thenReturn(Optional.of(ingesting));

        assertThrows(IllegalStateException.class, () -> service.handle(new ReprocessDocumentCommand("doc-rep-1")));
        verify(repository, times(1)).findById(documentId);
    }

    @Test
    @DisplayName("重处理命中时应更新状态并清理旧向量")
    void handle_shouldRequestReprocess_whenAllowed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(repository, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-rep-2");
        Document failed = new Document(
                documentId,
                "kb-1",
                "hash-2",
                "b.txt",
                1L,
                UploadStatus.FAILED,
                "error",
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
        when(repository.findById(documentId)).thenReturn(Optional.of(failed));
        when(repository.requestReprocess(eq(documentId), eq(UploadStatus.FAILED), eq("v2"), any(Instant.class)))
                .thenReturn(true);

        DocumentStatusResult result = service.handle(new ReprocessDocumentCommand("doc-rep-2"));

        assertEquals("doc-rep-2", result.documentId().value());
        assertEquals(UploadStatus.UPLOADED, result.status());
        verify(vectorIndexer, times(1)).deleteByDocumentIdAndSplitVersion(documentId, "v1");
    }

    @Test
    @DisplayName("文档不存在时应抛出 DocumentNotFoundException")
    void handle_shouldThrow_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(repository, vectorIndexer);
        when(repository.findById(new DocumentId("doc-missing"))).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.handle(new ReprocessDocumentCommand("doc-missing")));
    }
}
