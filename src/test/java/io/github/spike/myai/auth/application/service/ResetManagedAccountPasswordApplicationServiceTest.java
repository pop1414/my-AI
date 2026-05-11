package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.ResetManagedAccountPasswordCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class ResetManagedAccountPasswordApplicationServiceTest {

    @Test
    @DisplayName("重置密码成功后应清空锁定状态并返回最新账号信息")
    void handle_shouldResetPassword() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2"))
                .thenReturn(Optional.of(new ManagedAccount(
                        "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 3, Instant.parse("2026-05-11T00:00:00Z"))))
                .thenReturn(Optional.of(new ManagedAccount(
                        "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null)));
        when(passwordEncoder.encode("new-secret")).thenReturn("encoded-new-secret");
        when(repository.resetPassword(eq("default"), eq("user-2"), eq("encoded-new-secret"), any())).thenReturn(true);
        ResetManagedAccountPasswordApplicationService service = new ResetManagedAccountPasswordApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                repository,
                passwordEncoder,
                auditEventRepository);

        var result = service.handle(new ResetManagedAccountPasswordCommand("user-2", "new-secret"));

        assertEquals(0, result.failedLoginCount());
        assertEquals(null, result.lockedUntil());
        verify(repository).resetPassword(eq("default"), eq("user-2"), eq("encoded-new-secret"), any());
    }

    @Test
    @DisplayName("目标账号不存在时重置密码应失败")
    void handle_shouldThrowNotFound_whenAccountMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2")).thenReturn(Optional.empty());
        ResetManagedAccountPasswordApplicationService service = new ResetManagedAccountPasswordApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                repository,
                passwordEncoder,
                auditEventRepository);

        assertThrows(
                ManagedAccountNotFoundException.class,
                () -> service.handle(new ResetManagedAccountPasswordCommand("user-2", "new-secret")));
    }
}
