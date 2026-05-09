package io.github.spike.myai.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SpringSecurityCurrentUserProviderTest {

    private final SpringSecurityCurrentUserProvider provider = new SpringSecurityCurrentUserProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("已登录且 Principal 类型正确时应返回当前用户上下文")
    void currentUser_shouldReturnCurrentUser_whenPrincipalIsMyAiPrincipal() {
        MyAiPrincipal principal = new MyAiPrincipal(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WORKSPACE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<CurrentUser> currentUser = provider.currentUser();

        assertTrue(currentUser.isPresent());
        assertEquals("user-1", currentUser.get().userId());
        assertEquals("alice", currentUser.get().username());
        assertEquals("default", currentUser.get().workspaceId());
        assertEquals(WorkspaceRole.WORKSPACE_ADMIN, currentUser.get().workspaceRole());
    }

    @Test
    @DisplayName("requireCurrentUser 在已登录时应返回当前用户")
    void requireCurrentUser_shouldReturnCurrentUser_whenAuthenticated() {
        MyAiPrincipal principal = new MyAiPrincipal(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_OWNER);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WORKSPACE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentUser currentUser = provider.requireCurrentUser();

        assertEquals("user-1", currentUser.userId());
        assertEquals(WorkspaceRole.WORKSPACE_OWNER, currentUser.workspaceRole());
    }

    @Test
    @DisplayName("未登录时应返回空并在 requireCurrentUser 中抛出认证异常")
    void currentUser_shouldReturnEmpty_whenAuthenticationMissing() {
        Optional<CurrentUser> currentUser = provider.currentUser();

        assertTrue(currentUser.isEmpty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, provider::requireCurrentUser);
    }

    @Test
    @DisplayName("Principal 类型不匹配时应按未登录处理")
    void currentUser_shouldReturnEmpty_whenPrincipalTypeMismatch() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "alice",
                "secret",
                "ROLE_WORKSPACE_ADMIN");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<CurrentUser> currentUser = provider.currentUser();

        assertTrue(currentUser.isEmpty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, provider::requireCurrentUser);
    }

    @Test
    @DisplayName("认证对象未标记已认证时应按未登录处理")
    void currentUser_shouldReturnEmpty_whenAuthenticationIsNotAuthenticated() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new MyAiPrincipal(
                        "user-1",
                        "alice",
                        "Alice",
                        "default",
                        WorkspaceRole.WORKSPACE_MEMBER),
                "secret");
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<CurrentUser> currentUser = provider.currentUser();

        assertTrue(currentUser.isEmpty());
    }
}
