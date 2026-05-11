package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.UpsertDocumentGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
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

class UpsertDocumentGrantApplicationServiceTest {

    @Test
    @DisplayName("授予文档授权时应写入 grant 与审计")
    void handle_shouldSaveGrantAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        when(grantRepository.findActiveGrant("default", "doc-1", "user-2")).thenReturn(Optional.empty());
        UpsertDocumentGrantApplicationService service = new UpsertDocumentGrantApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        var result = service.handle(new UpsertDocumentGrantCommand("doc-1", "user-2", "DOC_ALLOW_READ"));

        assertEquals(DocumentPermission.DOC_ALLOW_READ, result.permission());
        verify(grantRepository).saveGrant(eq("default"), eq("doc-1"), eq("user-2"), eq(DocumentPermission.DOC_ALLOW_READ), any());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("DOCUMENT_GRANT_UPSERTED", auditCaptor.getValue().eventType());
    }

    @Test
    @DisplayName("授权权限未变化时应直接返回且不重复写入")
    void handle_shouldReturnExistingGrant_whenPermissionUnchanged() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        when(grantRepository.findActiveGrant("default", "doc-1", "user-2"))
                .thenReturn(Optional.of(new DocumentGrant("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE")));
        UpsertDocumentGrantApplicationService service = new UpsertDocumentGrantApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        var result = service.handle(new UpsertDocumentGrantCommand("doc-1", "user-2", "DOC_ALLOW_READ"));

        assertEquals(DocumentPermission.DOC_ALLOW_READ, result.permission());
        verify(grantRepository, never()).saveGrant(eq("default"), eq("doc-1"), eq("user-2"), eq(DocumentPermission.DOC_ALLOW_READ), any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("文档不存在时授予授权应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenDocumentMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-missing"))).thenReturn(Optional.empty());
        UpsertDocumentGrantApplicationService service = new UpsertDocumentGrantApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                ManagedDocumentNotFoundException.class,
                () -> service.handle(new UpsertDocumentGrantCommand("doc-missing", "user-2", "DOC_ALLOW_READ")));
    }

    @Test
    @DisplayName("目标成员不存在时授予授权应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenMemberMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        when(workspaceMemberRepository.findActiveMember("default", "user-missing")).thenReturn(Optional.empty());
        UpsertDocumentGrantApplicationService service = new UpsertDocumentGrantApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                WorkspaceMemberNotFoundException.class,
                () -> service.handle(new UpsertDocumentGrantCommand("doc-1", "user-missing", "DOC_ALLOW_READ")));
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
