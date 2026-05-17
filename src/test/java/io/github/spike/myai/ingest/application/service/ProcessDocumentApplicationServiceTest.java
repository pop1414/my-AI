package io.github.spike.myai.ingest.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.monitoring.IngestMetrics;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.SourceHint;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
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
        DocumentProcessingArtifactStorage artifactStorage = Mockito.mock(DocumentProcessingArtifactStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProcessDocumentApplicationService service =
                new ProcessDocumentApplicationService(
                        repository,
                        sourceStorage,
                        parser,
                        chunker,
                        artifactStorage,
                        vectorIndexer,
                        new RetryPolicy(),
                        new IngestMetrics(meterRegistry));

        DocumentId documentId = new DocumentId("doc-proc-1");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-1",
                "a.txt",
                100,
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(ingesting));
        when(sourceStorage.loadVersion(documentId, 1, "a.txt")).thenReturn(Optional.of("hello world".getBytes()));
        DocumentParseResult parseResult = new DocumentParseResult(
                "<html><body><p>hello world</p></body></html>",
                "<p>hello world</p>",
                "hello world",
                "{\"schema_version\":\"v1\"}");
        when(parser.parse(eq("a.txt"), any(byte[].class))).thenReturn(parseResult);
        when(chunker.chunk("hello world")).thenReturn(List.of(new DocumentChunk("hello world", SourceHint.none())));
        when(repository.markIndexed(
                        anyString(),
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        eq("{\"schema_version\":\"v1\"}"),
                        any(Instant.class)))
                .thenReturn(true);

        service.handle(documentId);

        verify(vectorIndexer, times(1)).index(eq(ingesting), eq(List.of(new DocumentChunk("hello world", SourceHint.none()))));
        verify(artifactStorage, times(1)).saveVersion(eq("default"), eq(documentId), eq(1), eq(parseResult));
        verify(repository, times(1))
                .markIndexed(
                        anyString(),
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        eq("{\"schema_version\":\"v1\"}"),
                        any(Instant.class));
        verify(repository, never())
                .markFailed(anyString(), eq(documentId), eq(UploadStatus.INGESTING), any(), any(), any(), any(), any(), any(Instant.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.process.success.total").counter().count());
    }

    @Test
    @DisplayName("处理异常时，应推进状态到 FAILED")
    void handle_shouldMarkFailed_whenProcessingFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentTextParser parser = Mockito.mock(DocumentTextParser.class);
        DocumentChunker chunker = Mockito.mock(DocumentChunker.class);
        DocumentProcessingArtifactStorage artifactStorage = Mockito.mock(DocumentProcessingArtifactStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProcessDocumentApplicationService service =
                new ProcessDocumentApplicationService(
                        repository,
                        sourceStorage,
                        parser,
                        chunker,
                        artifactStorage,
                        vectorIndexer,
                        new RetryPolicy(),
                        new IngestMetrics(meterRegistry));

        DocumentId documentId = new DocumentId("doc-proc-2");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-2",
                "b.txt",
                100,
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(ingesting));
        when(sourceStorage.loadVersion(documentId, 1, "b.txt")).thenReturn(Optional.empty());
        when(repository.markFailed(
                        anyString(),
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        any(String.class),
                        eq(null),
                        any(),
                        any(),
                        any(),
                        any(Instant.class)))
                .thenReturn(true);

        service.handle(documentId);

        verify(repository, times(1))
                .markFailed(anyString(), eq(documentId), eq(UploadStatus.INGESTING), any(String.class), eq(null), any(), any(), any(), any(Instant.class));
        verify(vectorIndexer, never()).index(any(Document.class), any(List.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.process.failed.total").counter().count());
    }

    @Test
    @DisplayName("瞬时错误时，应安排重试并增加重试计数指标")
    void handle_shouldScheduleRetry_whenTransientError() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentTextParser parser = Mockito.mock(DocumentTextParser.class);
        DocumentChunker chunker = Mockito.mock(DocumentChunker.class);
        DocumentProcessingArtifactStorage artifactStorage = Mockito.mock(DocumentProcessingArtifactStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ProcessDocumentApplicationService service =
                new ProcessDocumentApplicationService(
                        repository,
                        sourceStorage,
                        parser,
                        chunker,
                        artifactStorage,
                        vectorIndexer,
                        new RetryPolicy(),
                        new IngestMetrics(meterRegistry));

        DocumentId documentId = new DocumentId("doc-proc-3");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-3",
                "c.txt",
                100,
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(ingesting));
        when(sourceStorage.loadVersion(documentId, 1, "c.txt"))
                .thenThrow(new RuntimeException(new SocketTimeoutException("timeout")));
        when(repository.markRetry(
                        anyString(),
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        eq(1),
                        any(Instant.class),
                        any(String.class),
                        any(String.class),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(true);

        service.handle(documentId);

        verify(repository, times(1))
                .markRetry(
                        anyString(),
                        eq(documentId),
                        eq(UploadStatus.INGESTING),
                        eq(1),
                        any(Instant.class),
                        any(String.class),
                        any(String.class),
                        any(Instant.class),
                        any(Instant.class));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.process.retry_scheduled.total").counter().count());
    }
}
