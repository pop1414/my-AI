package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.monitoring.IngestMetrics;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.github.spike.myai.shared.rest.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-del-1");
        Document indexed = new Document(
                documentId,
                "workspace-a",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(indexed));
        when(repository.markDeleting(anyString(), eq(documentId), eq(UploadStatus.INDEXED), any(Instant.class))).thenReturn(true);
        when(repository.markDeleted(anyString(), eq(documentId), any(Instant.class))).thenReturn(true);

        service.handle(new DeleteDocumentCommand("doc-del-1"));

        verify(sourceStorage, times(1)).deleteByDocumentId(documentId);
        verify(vectorIndexer, times(1)).deleteByDocumentId(documentId);
        verify(repository, times(1)).markDeleted(anyString(), eq(documentId), any(Instant.class));
        verify(repository, never()).rollbackDeleting(anyString(), any(), any(), any());
        verify(authorizationService).requireCanManageDocument(any(CurrentUser.class), eq("doc-del-1"), eq("kb-1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.delete.success.total").counter().count());
    }

    @Test
    @DisplayName("删除成功后审计写入失败时，不应回滚删除结果")
    void handle_shouldKeepDeleteSuccess_whenAuditSaveFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        auditEventRepository);

        DocumentId documentId = new DocumentId("doc-del-audit");
        Document indexed = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-audit",
                "audit.txt",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(indexed));
        when(repository.markDeleting(anyString(), eq(documentId), eq(UploadStatus.INDEXED), any(Instant.class))).thenReturn(true);
        when(repository.markDeleted(anyString(), eq(documentId), any(Instant.class))).thenReturn(true);
        Mockito.doThrow(new IllegalStateException("audit down")).when(auditEventRepository).save(any());

        service.handle(new DeleteDocumentCommand("doc-del-audit"));

        verify(repository, times(1)).markDeleted(anyString(), eq(documentId), any(Instant.class));
        verify(repository, never()).rollbackDeleting(anyString(), any(), any(), any());
        verify(sourceStorage, times(1)).deleteByDocumentId(documentId);
        verify(vectorIndexer, times(1)).deleteByDocumentId(documentId);
    }

    @Test
    @DisplayName("删除不存在文档时应抛出 DocumentNotFoundException")
    void handle_shouldThrow_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));
        when(repository.findById(anyString(), eq(new DocumentId("doc-missing")))).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.handle(new DeleteDocumentCommand("doc-missing")));
    }

    @Test
    @DisplayName("INGESTING 状态删除时应抛出冲突异常")
    void handle_shouldThrowConflict_whenIngesting() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-del-2");
        Document ingesting = new Document(
                documentId,
                "workspace-a",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(ingesting));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(new DeleteDocumentCommand("doc-del-2")));
        org.junit.jupiter.api.Assertions.assertEquals("VERSION_CONFLICT_STATE_CHANGED", ex.code());
        verify(repository, never()).markDeleting(anyString(), any(), any(), any());
        verify(authorizationService).requireCanManageDocument(any(CurrentUser.class), eq("doc-del-2"), eq("kb-1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.delete.conflict.total").counter().count());
    }

    @Test
    @DisplayName("UPLOADED 执行态删除时应抛出冲突异常")
    void handle_shouldThrowConflict_whenUploaded() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-del-uploaded");
        Document uploaded = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-uploaded",
                "uploaded.txt",
                1L,
                UploadStatus.UPLOADED,
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
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(uploaded));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(new DeleteDocumentCommand("doc-del-uploaded")));
        org.junit.jupiter.api.Assertions.assertEquals("VERSION_CONFLICT_STATE_CHANGED", ex.code());

        verify(repository, never()).markDeleting(anyString(), any(), any(), any());
        verify(sourceStorage, never()).deleteByDocumentId(any());
        verify(vectorIndexer, never()).deleteByDocumentId(any());
        verify(authorizationService).requireCanManageDocument(any(CurrentUser.class), eq("doc-del-uploaded"), eq("kb-1"));
        org.junit.jupiter.api.Assertions.assertEquals(
                1.0, meterRegistry.get("myai.ingest.delete.conflict.total").counter().count());
    }

    @Test
    @DisplayName("expectedLatestVersionNumber 过期时，删除应返回 latest 冲突错误码")
    void handle_shouldThrowStaleLatestVersion_whenExpectedVersionStale() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-del-stale");
        Document indexed = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-stale",
                "stale.txt",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(indexed));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.handle(new DeleteDocumentCommand("doc-del-stale", 2)));

        org.junit.jupiter.api.Assertions.assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository, never()).markDeleting(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("资源删除失败时应回滚状态并抛出删除失败异常")
    void handle_shouldRollback_whenDeleteFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DeleteDocumentApplicationService service =
                new DeleteDocumentApplicationService(
                        repository,
                        sourceStorage,
                        vectorIndexer,
                        new IngestMetrics(meterRegistry),
                        currentUserProvider,
                        authorizationService,
                        Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-del-3");
        Document failed = new Document(
                documentId,
                "workspace-a",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(failed));
        when(repository.markDeleting(anyString(), eq(documentId), eq(UploadStatus.FAILED), any(Instant.class))).thenReturn(true);
        Mockito.doThrow(new IllegalStateException("vector down"))
                .when(vectorIndexer)
                .deleteByDocumentId(documentId);
        when(repository.rollbackDeleting(anyString(), eq(documentId), eq(UploadStatus.FAILED), any(Instant.class)))
                .thenReturn(true);

        assertThrows(DocumentDeleteFailedException.class, () -> service.handle(new DeleteDocumentCommand("doc-del-3")));
        verify(repository, times(1)).rollbackDeleting(anyString(), eq(documentId), eq(UploadStatus.FAILED), any(Instant.class));
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
