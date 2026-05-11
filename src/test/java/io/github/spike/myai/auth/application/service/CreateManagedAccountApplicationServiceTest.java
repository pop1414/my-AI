package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.CreateManagedAccountCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class CreateManagedAccountApplicationServiceTest {

    @Test
    @DisplayName("创建账号成功时应写入用户并记录审计")
    void handle_shouldCreateManagedAccount() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.existsUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(repository.createAccount(eq("default"), eq("bob"), eq("Bob"), eq("encoded-secret"), eq(WorkspaceRole.WORKSPACE_MEMBER), any()))
                .thenReturn(new ManagedAccount(
                        "user-2",
                        "bob",
                        "Bob",
                        "ACTIVE",
                        "default",
                        WorkspaceRole.WORKSPACE_MEMBER,
                        "ACTIVE",
                        0,
                        null));
        CreateManagedAccountApplicationService service = new CreateManagedAccountApplicationService(
                authorizationService,
                repository,
                passwordEncoder,
                auditEventRepository);

        var result = service.handle(new CreateManagedAccountCommand("bob", "Bob", "secret123", "WORKSPACE_MEMBER"));

        assertEquals("user-2", result.userId());
        assertEquals("ACTIVE", result.userStatus());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("MANAGED_ACCOUNT_CREATED", auditCaptor.getValue().eventType());
    }

    @Test
    @DisplayName("用户名已存在时应返回冲突语义异常")
    void handle_shouldThrowConflict_whenUsernameExists() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ManagedAccountRepository repository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.existsUsername("bob")).thenReturn(true);
        CreateManagedAccountApplicationService service = new CreateManagedAccountApplicationService(
                authorizationService,
                repository,
                passwordEncoder,
                auditEventRepository);

        assertThrows(
                ManagedAccountUsernameConflictException.class,
                () -> service.handle(new CreateManagedAccountCommand("bob", "Bob", "secret123", "WORKSPACE_MEMBER")));

        verify(repository, never()).createAccount(any(), any(), any(), any(), any(), any());
        verify(auditEventRepository, never()).save(any());
    }
}
