package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * DeleteDocumentApplicationService 单元测试。
 */
class DeleteDocumentApplicationServiceTest {

    @Test
    @DisplayName("删除命中时应完成删除并收口到 DELETED")
    void handle_shouldDeleteDocument_whenAllowed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(repository, sourceStorage, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-del-1");
        Document indexed = new Document(
                documentId,
                "kb-1",
                "hash-1",
                "a.txt",
                1L,
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
        when(repository.findById(documentId)).thenReturn(Optional.of(indexed));
        when(repository.markDeleting(eq(documentId), eq(UploadStatus.INDEXED), any(Instant.class))).thenReturn(true);
        when(repository.markDeleted(eq(documentId), any(Instant.class))).thenReturn(true);

        service.handle(new DeleteDocumentCommand("doc-del-1"));

        verify(sourceStorage, times(1)).deleteByDocumentId(documentId);
        verify(vectorIndexer, times(1)).deleteByDocumentId(documentId);
        verify(repository, times(1)).markDeleted(eq(documentId), any(Instant.class));
        verify(repository, never()).rollbackDeleting(any(), any(), any());
    }

    @Test
    @DisplayName("删除不存在文档时应抛出 DocumentNotFoundException")
    void handle_shouldThrow_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(repository, sourceStorage, vectorIndexer);
        when(repository.findById(new DocumentId("doc-missing"))).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.handle(new DeleteDocumentCommand("doc-missing")));
    }

    @Test
    @DisplayName("INGESTING 状态删除时应抛出冲突异常")
    void handle_shouldThrowConflict_whenIngesting() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(repository, sourceStorage, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-del-2");
        Document ingesting = new Document(
                documentId,
                "kb-1",
                "hash-2",
                "b.txt",
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

        assertThrows(DocumentDeleteConflictException.class, () -> service.handle(new DeleteDocumentCommand("doc-del-2")));
        verify(repository, never()).markDeleting(any(), any(), any());
    }

    @Test
    @DisplayName("资源删除失败时应回滚状态并抛出删除失败异常")
    void handle_shouldRollback_whenDeleteFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(repository, sourceStorage, vectorIndexer);

        DocumentId documentId = new DocumentId("doc-del-3");
        Document failed = new Document(
                documentId,
                "kb-1",
                "hash-3",
                "c.txt",
                1L,
                UploadStatus.FAILED,
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
        when(repository.findById(documentId)).thenReturn(Optional.of(failed));
        when(repository.markDeleting(eq(documentId), eq(UploadStatus.FAILED), any(Instant.class))).thenReturn(true);
        Mockito.doThrow(new IllegalStateException("vector down"))
                .when(vectorIndexer)
                .deleteByDocumentId(documentId);
        when(repository.rollbackDeleting(eq(documentId), eq(UploadStatus.FAILED), any(Instant.class)))
                .thenReturn(true);

        assertThrows(DocumentDeleteFailedException.class, () -> service.handle(new DeleteDocumentCommand("doc-del-3")));
        verify(repository, times(1)).rollbackDeleting(eq(documentId), eq(UploadStatus.FAILED), any(Instant.class));
    }
}
