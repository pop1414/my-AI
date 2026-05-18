package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
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

class ListMemberDocumentGrantsApplicationServiceTest {

    @Test
    @DisplayName("查询成员文档授权时应过滤无父级知识库授权的历史脏数据")
    void handle_shouldFilterDocumentGrantWithoutKnowledgeBaseGrant() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard = Mockito.mock(DocumentGrantKnowledgeBaseGuard.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentGrantManagementRepository grantRepository = Mockito.mock(DocumentGrantManagementRepository.class);
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
        when(grantRepository.findActiveGrantsByUser("default", "user-2")).thenReturn(List.of(
                new DocumentGrant("default", "doc-allowed", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE"),
                new DocumentGrant("default", "doc-orphan", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE")));
        when(documentRepository.findById("default", new DocumentId("doc-allowed")))
                .thenReturn(Optional.of(document("doc-allowed", "kb-allowed")));
        when(documentRepository.findById("default", new DocumentId("doc-orphan")))
                .thenReturn(Optional.of(document("doc-orphan", "kb-orphan")));
        when(documentGrantKnowledgeBaseGuard.hasMemberKnowledgeBaseGrant("default", "user-2", "kb-allowed"))
                .thenReturn(true);
        when(documentGrantKnowledgeBaseGuard.hasMemberKnowledgeBaseGrant("default", "user-2", "kb-orphan"))
                .thenReturn(false);
        ListMemberDocumentGrantsApplicationService service = new ListMemberDocumentGrantsApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                documentGrantKnowledgeBaseGuard,
                workspaceMemberRepository,
                documentRepository,
                grantRepository);

        List<DocumentGrantResult> results = service.handle("user-2");

        assertEquals(1, results.size());
        assertEquals("doc-allowed", results.getFirst().documentId());
    }

    private static Document document(String documentId, String kbId) {
        Instant now = Instant.now();
        return new Document(
                new DocumentId(documentId),
                "default",
                kbId,
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
