package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.LoginCommand;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.BootstrapAdminAccount;
import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.LoginFailureState;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.BootstrapAdminRepository;
import io.github.spike.myai.auth.domain.port.LocalAccountRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdminApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-09T08:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("空成员库且配置完整时应创建初始管理员")
    void run_shouldCreateBootstrapAdmin_whenWorkspaceHasNoMembershipsAndCredentialsConfigured() throws Exception {
        AuthBootstrapAdminProperties properties = configuredProperties();
        BootstrapAdminRepository repository = Mockito.mock(BootstrapAdminRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        when(repository.countWorkspaceMemberships(WorkspaceConstants.DEFAULT_WORKSPACE_ID)).thenReturn(0);
        when(passwordEncoder.encode("secret")).thenReturn("{bcrypt}hash");
        when(repository.saveBootstrapAdmin(any())).thenReturn("user-1");

        BootstrapAdminApplicationService service = new BootstrapAdminApplicationService(
                properties,
                repository,
                passwordEncoder,
                FIXED_CLOCK);

        service.run(null);

        ArgumentCaptor<BootstrapAdminAccount> captor = ArgumentCaptor.forClass(BootstrapAdminAccount.class);
        verify(repository).saveBootstrapAdmin(captor.capture());
        BootstrapAdminAccount account = captor.getValue();
        assertNotNull(account.userId());
        assertEquals("owner", account.username());
        assertEquals("Owner", account.displayName());
        assertEquals("{bcrypt}hash", account.passwordHash());
        assertEquals(WorkspaceConstants.DEFAULT_WORKSPACE_ID, account.workspaceId());
        assertEquals("WORKSPACE_OWNER", account.role());
        assertEquals(NOW, account.createdAt());
    }

    @Test
    @DisplayName("默认工作区已有成员时不应重复创建初始管理员")
    void run_shouldSkipBootstrapAdmin_whenWorkspaceAlreadyHasMemberships() throws Exception {
        AuthBootstrapAdminProperties properties = configuredProperties();
        BootstrapAdminRepository repository = Mockito.mock(BootstrapAdminRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        when(repository.countWorkspaceMemberships(WorkspaceConstants.DEFAULT_WORKSPACE_ID)).thenReturn(1);

        BootstrapAdminApplicationService service = new BootstrapAdminApplicationService(
                properties,
                repository,
                passwordEncoder,
                FIXED_CLOCK);

        service.run(null);

        verify(repository).countWorkspaceMemberships(WorkspaceConstants.DEFAULT_WORKSPACE_ID);
        verify(repository, never()).saveBootstrapAdmin(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("未配置密码时应跳过创建且不访问数据库")
    void run_shouldSkipBootstrapAdmin_whenPasswordMissing() throws Exception {
        AuthBootstrapAdminProperties properties = new AuthBootstrapAdminProperties();
        properties.setUsername("owner");
        BootstrapAdminRepository repository = Mockito.mock(BootstrapAdminRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);

        BootstrapAdminApplicationService service = new BootstrapAdminApplicationService(
                properties,
                repository,
                passwordEncoder,
                FIXED_CLOCK);

        service.run(null);

        verifyNoInteractions(repository, passwordEncoder);
    }

    @Test
    @DisplayName("创建完成后初始管理员应可通过现有登录链路登录")
    void bootstrapAdmin_shouldLoginThroughExistingLoginUseCase() throws Exception {
        AuthBootstrapAdminProperties properties = configuredProperties();
        InMemoryBootstrapAdminRepository repository = new InMemoryBootstrapAdminRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        BootstrapAdminApplicationService bootstrapService = new BootstrapAdminApplicationService(
                properties,
                repository,
                passwordEncoder,
                FIXED_CLOCK);

        bootstrapService.run(null);

        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        AuthSecurityProperties securityProperties = new AuthSecurityProperties();
        securityProperties.setMaxFailedAttempts(3);
        securityProperties.setLockDuration(Duration.ofMinutes(10));
        LoginApplicationService loginService = new LoginApplicationService(
                repository,
                auditEventRepository,
                passwordEncoder,
                securityProperties,
                FIXED_CLOCK);

        var currentUser = loginService.handle(new LoginCommand("owner", "secret"));

        assertEquals(repository.userId, currentUser.userId());
        assertEquals("owner", currentUser.username());
        assertEquals("Owner", currentUser.displayName());
        assertEquals(WorkspaceConstants.DEFAULT_WORKSPACE_ID, currentUser.workspaceId());
        assertEquals("WORKSPACE_OWNER", currentUser.workspaceRole());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("LOGIN_SUCCESS", captor.getValue().eventType());
        assertTrue(repository.successfulLoginRecorded);
    }

    private static AuthBootstrapAdminProperties configuredProperties() {
        AuthBootstrapAdminProperties properties = new AuthBootstrapAdminProperties();
        properties.setUsername("owner");
        properties.setPassword("secret");
        properties.setDisplayName("Owner");
        return properties;
    }

    private static final class InMemoryBootstrapAdminRepository
            implements BootstrapAdminRepository, LocalAccountRepository {

        private String userId;

        private LoginAccount account;

        private boolean successfulLoginRecorded;

        @Override
        public int countWorkspaceMemberships(String workspaceId) {
            return account == null ? 0 : 1;
        }

        @Override
        public String saveBootstrapAdmin(BootstrapAdminAccount bootstrapAccount) {
            this.userId = bootstrapAccount.userId();
            this.account = new LoginAccount(
                    bootstrapAccount.userId(),
                    bootstrapAccount.username(),
                    bootstrapAccount.displayName(),
                    "ACTIVE",
                    bootstrapAccount.passwordHash(),
                    bootstrapAccount.workspaceId(),
                    bootstrapAccount.role(),
                    "ACTIVE",
                    0,
                    null);
            return bootstrapAccount.userId();
        }

        @Override
        public Optional<LoginAccount> findByUsername(String username) {
            if (account == null || !account.username().equals(username)) {
                return Optional.empty();
            }
            return Optional.of(account);
        }

        @Override
        public LoginFailureState recordFailedLogin(
                String userId,
                Instant failedAt,
                int maxFailedAttempts,
                Instant lockUntil) {
            return new LoginFailureState(1, null);
        }

        @Override
        public void recordSuccessfulLogin(String userId, Instant loginAt) {
            this.successfulLoginRecorded = true;
        }
    }
}
