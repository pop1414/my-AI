package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.GovernanceAccessDeniedException;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspaceGovernanceGuardTest {

    private final WorkspaceGovernanceGuard guard = new WorkspaceGovernanceGuard();

    @Test
    @DisplayName("ADMIN 不可操作 OWNER")
    void requireCanManageManagedAccount_shouldDenyAdminManagingOwner() {
        assertThrows(
                GovernanceAccessDeniedException.class,
                () -> guard.requireCanManageManagedAccount(
                        new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN),
                        WorkspaceRole.WORKSPACE_OWNER));
    }

    @Test
    @DisplayName("ADMIN 不可操作其他 ADMIN")
    void requireCanManageManagedAccount_shouldDenyAdminManagingAdmin() {
        assertThrows(
                GovernanceAccessDeniedException.class,
                () -> guard.requireCanManageManagedAccount(
                        new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN),
                        WorkspaceRole.WORKSPACE_ADMIN));
    }

    @Test
    @DisplayName("仅 OWNER 可创建 ADMIN")
    void requireCanCreateManagedAccount_shouldDenyAdminCreatingAdmin() {
        assertThrows(
                GovernanceAccessDeniedException.class,
                () -> guard.requireCanCreateManagedAccount(
                        new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN),
                        WorkspaceRole.WORKSPACE_ADMIN));
    }
}
