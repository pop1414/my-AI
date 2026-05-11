package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.RemoveManagedAccountMembershipCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RemoveManagedAccountMembershipApplicationServiceTest {

    @Test
    @DisplayName("移除成员关系成功时应更新成员状态并写审计")
    void handle_shouldDeactivateMembership() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2"))
                .thenReturn(Optional.of(new ManagedAccount(
                        "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null)));
        when(repository.deactivateMembership(eq("default"), eq("user-2"), any())).thenReturn(true);
        RemoveManagedAccountMembershipApplicationService service = new RemoveManagedAccountMembershipApplicationService(
                authorizationService,
                repository,
                auditEventRepository);

        service.handle(new RemoveManagedAccountMembershipCommand("user-2"));

        verify(repository).deactivateMembership(eq("default"), eq("user-2"), any());
        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("成员已被移除时应幂等返回")
    void handle_shouldReturnSilently_whenMembershipInactive() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2"))
                .thenReturn(Optional.of(new ManagedAccount(
                        "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "INACTIVE", 0, null)));
        RemoveManagedAccountMembershipApplicationService service = new RemoveManagedAccountMembershipApplicationService(
                authorizationService,
                repository,
                auditEventRepository);

        service.handle(new RemoveManagedAccountMembershipCommand("user-2"));

        verify(repository, never()).deactivateMembership(any(), any(), any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("目标账号不存在时移除成员关系应失败")
    void handle_shouldThrowNotFound_whenAccountMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2")).thenReturn(Optional.empty());
        RemoveManagedAccountMembershipApplicationService service = new RemoveManagedAccountMembershipApplicationService(
                authorizationService,
                repository,
                auditEventRepository);

        assertThrows(
                ManagedAccountNotFoundException.class,
                () -> service.handle(new RemoveManagedAccountMembershipCommand("user-2")));
    }
}
