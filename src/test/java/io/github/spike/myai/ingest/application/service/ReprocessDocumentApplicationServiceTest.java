package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.github.spike.myai.shared.rest.BusinessException;
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
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(
                repository,
                vectorIndexer,
                currentUserProvider,
                authorizationService,
                Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-rep-1");
        Document ingesting = new Document(
                documentId,
                "workspace-a",
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(ingesting));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(new ReprocessDocumentCommand("doc-rep-1")));
        assertEquals("VERSION_CONFLICT_STATE_CHANGED", ex.code());
        verify(repository, times(1)).findById(anyString(), eq(documentId));
        verify(authorizationService).requireCanContributeKnowledgeBase(any(CurrentUser.class), eq("kb-1"));
    }

    @Test
    @DisplayName("重处理命中时应更新状态并清理旧向量")
    void handle_shouldRequestReprocess_whenAllowed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(
                repository,
                vectorIndexer,
                currentUserProvider,
                authorizationService,
                Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-rep-2");
        Document failed = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                3,
                DocumentVersionOriginType.ROLLBACK,
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
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(failed));
        when(repository.requestReprocess(anyString(), eq(documentId), eq(UploadStatus.FAILED), eq("v2"), any(Instant.class)))
                .thenReturn(true);

        DocumentStatusResult result = service.handle(new ReprocessDocumentCommand("doc-rep-2"));

        assertEquals("doc-rep-2", result.documentId().value());
        assertEquals(3, result.latestVersionNumber());
        assertEquals("b.txt", result.latestFilename());
        assertEquals(DocumentVersionOriginType.ROLLBACK, result.latestVersionOriginType());
        assertEquals(UploadStatus.UPLOADED, result.status());
        verify(vectorIndexer, times(1)).deleteByDocumentIdAndSplitVersion(documentId, "v1");
    }

    @Test
    @DisplayName("文档不存在时应抛出 DocumentNotFoundException")
    void handle_shouldThrow_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(
                repository,
                vectorIndexer,
                currentUserProvider,
                authorizationService,
                Mockito.mock(AuditEventRepository.class));
        when(repository.findById(anyString(), eq(new DocumentId("doc-missing")))).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> service.handle(new ReprocessDocumentCommand("doc-missing")));
    }

    @Test
    @DisplayName("expectedLatestVersionNumber 过期时，重处理应返回 latest 冲突错误码")
    void handle_shouldThrowStaleLatestVersion_whenExpectedVersionStale() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentVectorIndexer vectorIndexer = Mockito.mock(DocumentVectorIndexer.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ReprocessDocumentApplicationService service = new ReprocessDocumentApplicationService(
                repository,
                vectorIndexer,
                currentUserProvider,
                authorizationService,
                Mockito.mock(AuditEventRepository.class));

        DocumentId documentId = new DocumentId("doc-rep-stale");
        Document failed = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-stale",
                "stale.txt",
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

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.handle(new ReprocessDocumentCommand("doc-rep-stale", 2)));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository, times(0)).requestReprocess(anyString(), any(), any(), any(), any());
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
