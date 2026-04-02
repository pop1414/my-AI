package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ClaimNextUploadedDocumentApplicationService 单元测试。
 */
class ClaimNextUploadedDocumentApplicationServiceTest {

    @Test
    @DisplayName("无 UPLOADED 候选时，应返回空")
    void handle_shouldReturnEmpty_whenNoUploadedDocument() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        when(repository.findOldestByStatus(UploadStatus.UPLOADED)).thenReturn(Optional.empty());
        ClaimNextUploadedDocumentApplicationService service = new ClaimNextUploadedDocumentApplicationService(repository);

        Optional<DocumentId> result = service.handle();

        assertTrue(result.isEmpty());
        verify(repository, times(1)).findOldestByStatus(UploadStatus.UPLOADED);
        verify(repository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CAS 抢占成功时，应返回文档资产 ID")
    void handle_shouldReturnDocumentId_whenClaimSuccess() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        Document uploaded = new Document(
                new DocumentId("doc-claim-1"),
                "kb-1",
                "hash-1",
                "a.txt",
                10L,
                UploadStatus.UPLOADED,
                null,
                Instant.now(),
                Instant.now());
        when(repository.findOldestByStatus(UploadStatus.UPLOADED)).thenReturn(Optional.of(uploaded));
        when(repository.compareAndSetStatus(
                        eq(uploaded.documentId()),
                        eq(UploadStatus.UPLOADED),
                        eq(UploadStatus.INGESTING),
                        eq(null),
                        any(Instant.class)))
                .thenReturn(true);
        ClaimNextUploadedDocumentApplicationService service = new ClaimNextUploadedDocumentApplicationService(repository);

        Optional<DocumentId> result = service.handle();

        assertTrue(result.isPresent());
        assertEquals("doc-claim-1", result.get().value());
        verify(repository, times(1)).findOldestByStatus(UploadStatus.UPLOADED);
        verify(repository, times(1))
                .compareAndSetStatus(eq(uploaded.documentId()), eq(UploadStatus.UPLOADED), eq(UploadStatus.INGESTING), eq(null), any(Instant.class));
    }

    @Test
    @DisplayName("CAS 抢占失败时，应返回空")
    void handle_shouldReturnEmpty_whenClaimFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        Document uploaded = new Document(
                new DocumentId("doc-claim-2"),
                "kb-1",
                "hash-2",
                "b.txt",
                10L,
                UploadStatus.UPLOADED,
                null,
                Instant.now(),
                Instant.now());
        when(repository.findOldestByStatus(UploadStatus.UPLOADED)).thenReturn(Optional.of(uploaded));
        when(repository.compareAndSetStatus(
                        eq(uploaded.documentId()),
                        eq(UploadStatus.UPLOADED),
                        eq(UploadStatus.INGESTING),
                        eq(null),
                        any(Instant.class)))
                .thenReturn(false);
        ClaimNextUploadedDocumentApplicationService service = new ClaimNextUploadedDocumentApplicationService(repository);

        Optional<DocumentId> result = service.handle();

        assertTrue(result.isEmpty());
        verify(repository, times(1))
                .compareAndSetStatus(eq(uploaded.documentId()), eq(UploadStatus.UPLOADED), eq(UploadStatus.INGESTING), eq(null), any(Instant.class));
    }
}

