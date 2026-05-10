package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.LoginCommand;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.LoginFailureState;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.LocalAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-08T10:00:00Z");

    private LocalAccountRepository localAccountRepository;
    private AuditEventRepository auditEventRepository;
    private PasswordEncoder passwordEncoder;
    private LoginApplicationService service;

    @BeforeEach
    void setUp() {
        this.localAccountRepository = Mockito.mock(LocalAccountRepository.class);
        this.auditEventRepository = Mockito.mock(AuditEventRepository.class);
        this.passwordEncoder = Mockito.mock(PasswordEncoder.class);
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.setMaxFailedAttempts(3);
        properties.setLockDuration(Duration.ofMinutes(10));
        this.service = new LoginApplicationService(
                localAccountRepository,
                auditEventRepository,
                passwordEncoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("登录成功时应清空失败状态并写入成功审计")
    void handle_shouldReturnCurrentUser_whenPasswordMatches() {
        LoginAccount account = activeAccount();
        when(localAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "{bcrypt}hash")).thenReturn(true);

        var result = service.handle(new LoginCommand("alice", "secret"));

        assertEquals("user-1", result.userId());
        assertEquals("alice", result.username());
        assertEquals("default", result.workspaceId());
        assertEquals(WorkspaceRole.WORKSPACE_ADMIN, result.workspaceRole());
        verify(localAccountRepository).recordSuccessfulLogin("user-1", NOW);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("LOGIN_SUCCESS", captor.getValue().eventType());
        assertEquals("SUCCESS", captor.getValue().outcome());
    }

    @Test
    @DisplayName("密码错误时应累计失败次数并写入失败审计")
    void handle_shouldRecordFailure_whenPasswordInvalid() {
        LoginAccount account = activeAccount();
        when(localAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("bad", "{bcrypt}hash")).thenReturn(false);
        when(localAccountRepository.recordFailedLogin(eq("user-1"), eq(NOW), eq(3), eq(NOW.plus(Duration.ofMinutes(10)))))
                .thenReturn(new LoginFailureState(1, null));

        assertThrows(BadCredentialsException.class, () -> service.handle(new LoginCommand("alice", "bad")));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("LOGIN_FAILURE", captor.getValue().eventType());
        assertEquals("BAD_CREDENTIALS", captor.getValue().reason());
    }

    @Test
    @DisplayName("失败达到阈值时应锁定账号并拒绝登录")
    void handle_shouldReject_whenFailureReachesLockThreshold() {
        LoginAccount account = activeAccount();
        Instant lockedUntil = NOW.plus(Duration.ofMinutes(10));
        when(localAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("bad", "{bcrypt}hash")).thenReturn(false);
        when(localAccountRepository.recordFailedLogin(any(), any(), anyInt(), any()))
                .thenReturn(new LoginFailureState(3, lockedUntil));

        LockedException ex = assertThrows(LockedException.class, () -> service.handle(new LoginCommand("alice", "bad")));
        assertTrue(ex.getMessage().contains("account is locked until"));
        assertTrue(ex.getMessage().contains(lockedUntil.toString()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("ACCOUNT_LOCKED", captor.getValue().reason());
    }

    private static LoginAccount activeAccount() {
        return new LoginAccount(
                "user-1",
                "alice",
                "Alice",
                "ACTIVE",
                "{bcrypt}hash",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN,
                "ACTIVE",
                0,
                null);
    }
}
