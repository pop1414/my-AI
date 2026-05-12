package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.ReplaceMemberKnowledgeBaseGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReplaceMemberKnowledgeBaseGrantsApplicationServiceTest {

    @Test
    @DisplayName("全量覆盖知识库授权时应新增缺失项并回收未提交项")
    void handle_shouldReplaceMemberKnowledgeBaseGrants() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember(
                        "user-2",
                        "bob",
                        "Bob",
                        "default",
                        WorkspaceRole.WORKSPACE_MEMBER,
                        "ACTIVE")));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase(
                        "kb-1",
                        "default",
                        "知识库1",
                        "",
                        KnowledgeBaseStatus.ACTIVE,
                        Instant.now(),
                        Instant.now())));
        when(grantRepository.findActiveGrantsByUser("default", "user-2"))
                .thenReturn(List.of(new KnowledgeBaseGrant(
                        "default",
                        "kb-legacy",
                        "user-2",
                        "bob",
                        "Bob",
                        KnowledgeBaseRole.KB_READER,
                        "ACTIVE")))
                .thenReturn(List.of(new KnowledgeBaseGrant(
                        "default",
                        "kb-1",
                        "user-2",
                        "bob",
                        "Bob",
                        KnowledgeBaseRole.KB_MANAGER,
                        "ACTIVE")));
        when(grantRepository.disableGrant(eq("default"), eq("kb-legacy"), eq("user-2"), any()))
                .thenReturn(true);

        ReplaceMemberKnowledgeBaseGrantsApplicationService service =
                new ReplaceMemberKnowledgeBaseGrantsApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        workspaceMemberRepository,
                        knowledgeBaseRepository,
                        grantRepository,
                        auditEventRepository);

        var result = service.handle(new ReplaceMemberKnowledgeBaseGrantsCommand(
                "user-2",
                List.of(new ReplaceMemberKnowledgeBaseGrantsCommand.Assignment(
                        "kb-1",
                        "KB_MANAGER"))));

        assertEquals(1, result.size());
        assertEquals("kb-1", result.getFirst().kbId());
        verify(grantRepository).saveGrant(
                eq("default"),
                eq("kb-1"),
                eq("user-2"),
                eq(KnowledgeBaseRole.KB_MANAGER),
                any());
        verify(grantRepository).disableGrant(
                eq("default"),
                eq("kb-legacy"),
                eq("user-2"),
                any());
    }
}
