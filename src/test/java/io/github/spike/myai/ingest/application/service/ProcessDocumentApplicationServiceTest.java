package io.github.spike.myai.ingest.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ProcessDocumentApplicationService 单元测试。
 */
class ProcessDocumentApplicationServiceTest {

    @Test
    @DisplayName("处理成功时，应推进状态到 INDEXED")
    void handle_shouldMarkIndexed_whenProcessingSucceeded() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentTextParser parser = Mockito.mock(DocumentTextParser.class);
        DocumentChunker chunker = Mockito.mock(DocumentChunker.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        ProcessDocumentApplicationService service =
                new ProcessDocumentApplicationService(repository, sourceStorage, parser, chunker, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-proc-1");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-1",
                "a.txt",
                100,
                UploadStatus.INGESTING,
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(documentId)).thenReturn(Optional.of(ingesting));
        when(sourceStorage.load(documentId, "a.txt")).thenReturn(Optional.of("hello world".getBytes()));
        when(parser.parse(eq("a.txt"), any(byte[].class))).thenReturn("hello world");
        when(chunker.chunk("hello world")).thenReturn(List.of("hello world"));
        when(repository.compareAndSetStatus(
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        eq(UploadStatus.INDEXED),
                        eq(null),
                        any(Instant.class)))
                .thenReturn(true);

        service.handle(documentId);

        verify(vectorIndexer, times(1)).index(eq(ingesting), eq(List.of("hello world")));
        verify(repository, times(1))
                .compareAndSetStatus(eq(documentId), eq(UploadStatus.INGESTING), eq(UploadStatus.INDEXED), eq(null), any(Instant.class));
        verify(repository, never())
                .compareAndSetStatus(eq(documentId), eq(UploadStatus.INGESTING), eq(UploadStatus.FAILED), any(), any(Instant.class));
    }

    @Test
    @DisplayName("处理异常时，应推进状态到 FAILED")
    void handle_shouldMarkFailed_whenProcessingFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentTextParser parser = Mockito.mock(DocumentTextParser.class);
        DocumentChunker chunker = Mockito.mock(DocumentChunker.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        ProcessDocumentApplicationService service =
                new ProcessDocumentApplicationService(repository, sourceStorage, parser, chunker, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-proc-2");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-2",
                "b.txt",
                100,
                UploadStatus.INGESTING,
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(documentId)).thenReturn(Optional.of(ingesting));
        when(sourceStorage.load(documentId, "b.txt")).thenReturn(Optional.empty());

        service.handle(documentId);

        verify(repository, times(1))
                .compareAndSetStatus(eq(documentId), eq(UploadStatus.INGESTING), eq(UploadStatus.FAILED), any(String.class), any(Instant.class));
        verify(vectorIndexer, never()).index(any(Document.class), any(List.class));
    }
}

