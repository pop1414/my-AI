package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.RevokeDocumentGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.DocumentGrantNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RevokeDocumentGrantApplicationServiceTest {

    @Test
    @DisplayName("回收文档授权时应更新状态并写入审计")
    void handle_shouldDisableGrantAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(grantRepository.findActiveGrant("default", "doc-1", "user-2"))
                .thenReturn(Optional.of(new DocumentGrant("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_MANAGE, "ACTIVE")));
        when(grantRepository.disableGrant(eq("default"), eq("doc-1"), eq("user-2"), any())).thenReturn(true);
        RevokeDocumentGrantApplicationService service = new RevokeDocumentGrantApplicationService(
                authorizationService,
                documentRepository,
                grantRepository,
                auditEventRepository);

        service.handle(new RevokeDocumentGrantCommand("doc-1", "user-2"));

        verify(grantRepository).disableGrant(eq("default"), eq("doc-1"), eq("user-2"), any());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("DOCUMENT_GRANT_REVOKED", auditCaptor.getValue().eventType());
    }

    @Test
    @DisplayName("授权不存在时回收应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenGrantMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(grantRepository.findActiveGrant("default", "doc-1", "user-missing")).thenReturn(Optional.empty());
        RevokeDocumentGrantApplicationService service = new RevokeDocumentGrantApplicationService(
                authorizationService,
                documentRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                DocumentGrantNotFoundException.class,
                () -> service.handle(new RevokeDocumentGrantCommand("doc-1", "user-missing")));
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
                now,
                now);
    }
}
