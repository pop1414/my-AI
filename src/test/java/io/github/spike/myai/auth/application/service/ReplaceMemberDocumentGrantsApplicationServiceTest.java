package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.ReplaceMemberDocumentGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.AuditEvent;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReplaceMemberDocumentGrantsApplicationServiceTest {

    @Test
    @DisplayName("批量替换成员文档授权时应拒绝成员未授权知识库下的文档")
    void handle_shouldRejectDocumentOutsideMemberKnowledgeBaseGrants() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard = Mockito.mock(DocumentGrantKnowledgeBaseGuard.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember(
                        "user-2",
                        "bob",
                        "Bob",
                        "default",
                        WorkspaceRole.WORKSPACE_MEMBER,
                        "ACTIVE")));
        when(documentRepository.findById("default", new DocumentId("doc-1")))
                .thenReturn(Optional.of(document()));
        Mockito.doThrow(new IllegalArgumentException("document grant requires active knowledge base grant: kb-1"))
                .when(documentGrantKnowledgeBaseGuard)
                .requireMemberKnowledgeBaseGrant("default", "user-2", "kb-1");
        ReplaceMemberDocumentGrantsApplicationService service = new ReplaceMemberDocumentGrantsApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentGrantKnowledgeBaseGuard,
                workspaceMemberRepository,
                documentRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.handle(new ReplaceMemberDocumentGrantsCommand(
                        "user-2",
                        List.of(new ReplaceMemberDocumentGrantsCommand.Assignment("doc-1", "DOC_ALLOW_READ")))));
        verify(grantRepository, never()).saveGrant(any(), any(), any(), any(), any());
        verify(auditEventRepository, never()).save(any(AuditEvent.class));
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
