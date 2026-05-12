package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.UpdateManagedAccountStatusCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UpdateManagedAccountStatusApplicationServiceTest {

    @Test
    @DisplayName("更新账号状态成功时应写入审计")
    void handle_shouldUpdateUserStatus() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2"))
                .thenReturn(Optional.of(new ManagedAccount(
                        "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null)));
        when(repository.updateUserStatus(eq("default"), eq("user-2"), eq("DISABLED"), any())).thenReturn(true);
        UpdateManagedAccountStatusApplicationService service = new UpdateManagedAccountStatusApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                repository,
                auditEventRepository);

        var result = service.handle(new UpdateManagedAccountStatusCommand("user-2", "DISABLED"));

        assertEquals("DISABLED", result.userStatus());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("MANAGED_ACCOUNT_STATUS_UPDATED", auditCaptor.getValue().eventType());
    }

    @Test
    @DisplayName("账号不存在时应抛出未找到异常")
    void handle_shouldThrowNotFound_whenAccountMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findWorkspaceAccount("default", "user-2")).thenReturn(Optional.empty());
        UpdateManagedAccountStatusApplicationService service = new UpdateManagedAccountStatusApplicationService(
                authorizationService,
                workspaceGovernanceGuard,
                repository,
                auditEventRepository);

        assertThrows(
                ManagedAccountNotFoundException.class,
                () -> service.handle(new UpdateManagedAccountStatusCommand("user-2", "DISABLED")));

        verify(repository, never()).updateUserStatus(any(), any(), any(), any());
    }
}
