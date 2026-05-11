package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class UpdateWorkspaceMemberRoleApplicationServiceTest {

    @Test
    @DisplayName("管理员调整成员角色时应更新成员关系并写入审计")
    void handle_shouldUpdateWorkspaceRoleAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findActiveMember("workspace-a", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember(
                        "user-2", "bob", "Bob", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        when(repository.updateWorkspaceRole(
                eq("workspace-a"),
                eq("user-2"),
                eq(WorkspaceRole.WORKSPACE_ADMIN),
                any()))
                .thenReturn(true);
        UpdateWorkspaceMemberRoleApplicationService service =
                new UpdateWorkspaceMemberRoleApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        repository,
                        auditEventRepository);

        var result = service.handle(new UpdateWorkspaceMemberRoleCommand("user-2", "WORKSPACE_ADMIN"));

        assertEquals(WorkspaceRole.WORKSPACE_ADMIN, result.workspaceRole());
        verify(repository).updateWorkspaceRole(eq("workspace-a"), eq("user-2"), eq(WorkspaceRole.WORKSPACE_ADMIN), any());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("WORKSPACE_MEMBER_ROLE_UPDATED", auditCaptor.getValue().eventType());
        assertEquals("WORKSPACE_MEMBERSHIP", auditCaptor.getValue().targetType());
    }

    @Test
    @DisplayName("角色未变化时应直接返回且不写审计")
    void handle_shouldReturnCurrentMember_whenRoleUnchanged() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findActiveMember("workspace-a", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember(
                        "user-2", "bob", "Bob", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        UpdateWorkspaceMemberRoleApplicationService service =
                new UpdateWorkspaceMemberRoleApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        repository,
                        auditEventRepository);

        var result = service.handle(new UpdateWorkspaceMemberRoleCommand("user-2", "WORKSPACE_MEMBER"));

        assertEquals(WorkspaceRole.WORKSPACE_MEMBER, result.workspaceRole());
        verify(repository, never()).updateWorkspaceRole(eq("workspace-a"), eq("user-2"), eq(WorkspaceRole.WORKSPACE_MEMBER), any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("目标成员不存在时应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenTargetMemberMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findActiveMember("workspace-a", "user-missing")).thenReturn(Optional.empty());
        UpdateWorkspaceMemberRoleApplicationService service =
                new UpdateWorkspaceMemberRoleApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        repository,
                        auditEventRepository);

        assertThrows(
                WorkspaceMemberNotFoundException.class,
                () -> service.handle(new UpdateWorkspaceMemberRoleCommand("user-missing", "WORKSPACE_ADMIN")));

        verify(repository, never()).updateWorkspaceRole(eq("workspace-a"), eq("user-missing"), eq(WorkspaceRole.WORKSPACE_ADMIN), any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("无工作区管理权限时调整成员角色应被拒绝")
    void handle_shouldDeny_whenUserCannotManageWorkspace() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenThrow(new AccessDeniedException("workspace manage access denied"));
        UpdateWorkspaceMemberRoleApplicationService service =
                new UpdateWorkspaceMemberRoleApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        repository,
                        auditEventRepository);

        assertThrows(
                AccessDeniedException.class,
                () -> service.handle(new UpdateWorkspaceMemberRoleCommand("user-2", "WORKSPACE_ADMIN")));

        verify(repository, never()).findActiveMember("workspace-a", "user-2");
        verify(auditEventRepository, never()).save(any());
    }
}
