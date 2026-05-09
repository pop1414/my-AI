package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
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
 * GetDocumentStatusApplicationService 的应用层单元测试。
 */
class GetDocumentStatusApplicationServiceTest {

    @Test
    @DisplayName("查询命中时，应返回文档状态结果")
    void handle_shouldReturnResult_whenFound() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        GetDocumentStatusApplicationService service =
                new GetDocumentStatusApplicationService(repository, currentUserProvider, authorizationService);
        DocumentId documentId = new DocumentId("doc-100");
        Document document = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-100",
                "a.txt",
                128,
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
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(document));

        DocumentStatusResult result = service.handle(new GetDocumentStatusQuery("doc-100"));

        assertEquals("doc-100", result.documentId().value());
        assertEquals(UploadStatus.INDEXED, result.status());
        verify(repository, times(1)).findById(eq("workspace-a"), eq(documentId));
        verify(authorizationService).requireCanReadDocument(any(CurrentUser.class), eq("doc-100"), eq("kb-1"));
    }

    @Test
    @DisplayName("查询未命中时，应抛出 DocumentNotFoundException")
    void handle_shouldThrowException_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        GetDocumentStatusApplicationService service =
                new GetDocumentStatusApplicationService(repository, currentUserProvider, authorizationService);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-missing")))).thenReturn(Optional.empty());

        assertThrows(
                DocumentNotFoundException.class,
                () -> service.handle(new GetDocumentStatusQuery("doc-missing")));
    }

    @Test
    @DisplayName("已删除文档查询时，应返回 DELETED")
    void handle_shouldReturnDeleted_whenDeleted() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        GetDocumentStatusApplicationService service =
                new GetDocumentStatusApplicationService(repository, currentUserProvider, authorizationService);
        DocumentId documentId = new DocumentId("doc-deleted");
        Document document = new Document(
                documentId,
                "workspace-a",
                "kb-1",
                "hash-del",
                "del.txt",
                128,
                UploadStatus.DELETED,
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
        when(repository.findById(anyString(), eq(documentId))).thenReturn(Optional.of(document));

        DocumentStatusResult result = service.handle(new GetDocumentStatusQuery("doc-deleted"));

        assertEquals(UploadStatus.DELETED, result.status());
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
