package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListDocumentGrantsApplicationServiceTest {

    @Test
    @DisplayName("管理员查询文档授权列表时应返回 ACTIVE grant")
    void handle_shouldReturnDocumentGrants() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(grantRepository.findActiveGrants("default", "doc-1")).thenReturn(List.of(
                new DocumentGrant("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE")));
        ListDocumentGrantsApplicationService service = new ListDocumentGrantsApplicationService(
                authorizationService,
                documentRepository,
                grantRepository);

        var result = service.handle("doc-1");

        assertEquals(1, result.size());
        assertEquals("user-2", result.getFirst().userId());
        assertEquals(DocumentPermission.DOC_ALLOW_READ, result.getFirst().permission());
        verify(grantRepository).findActiveGrants("default", "doc-1");
    }

    @Test
    @DisplayName("文档不存在时查询授权列表应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenDocumentMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-missing"))).thenReturn(Optional.empty());
        ListDocumentGrantsApplicationService service = new ListDocumentGrantsApplicationService(
                authorizationService,
                documentRepository,
                grantRepository);

        assertThrows(ManagedDocumentNotFoundException.class, () -> service.handle("doc-missing"));
    }

    private static Document document() {
        Instant now = Instant.now();
        return new Document(
                new DocumentId("doc-1"),
                "default",
                "kb-1",
                "hash-1",
                "a.txt",
                100L,
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
                now,
                now);
    }
}
