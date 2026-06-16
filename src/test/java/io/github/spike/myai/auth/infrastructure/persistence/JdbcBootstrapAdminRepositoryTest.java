package io.github.spike.myai.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.domain.model.BootstrapAdminAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO(spike): Refactor to integration test
 *
 * Current implementation violates project rule:
 * "Do not mock JdbcTemplate/JDBC chain - SQL correctness must be verified via real database"
 *
 * Refactoring plan:
 * 1. Use Testcontainers for real PostgreSQL environment
 * 2. Remove JdbcTemplate mocks
 * 3. Verify SQL correctness via real database
 *
 * @see docs/project-context.md:187-188
 */
@Disabled("TODO: Refactor to integration test - remove JdbcTemplate mock")
class JdbcBootstrapAdminRepositoryTest {

    @Test
    @DisplayName("构造初始化不应执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcBootstrapAdminRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("countWorkspaceMemberships 应按工作区统计成员关系")
    void countWorkspaceMemberships_shouldFilterByWorkspaceId() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcBootstrapAdminRepository repository = new JdbcBootstrapAdminRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Integer.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID)))
                .thenReturn(0);

        int count = repository.countWorkspaceMemberships(WorkspaceConstants.DEFAULT_WORKSPACE_ID);

        assertEquals(0, count);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                eq(Integer.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID));
        assertTrue(sqlCaptor.getValue().contains("FROM workspace_memberships"));
        assertTrue(sqlCaptor.getValue().contains("WHERE workspace_id = ?"));
    }

    @Test
    @DisplayName("saveBootstrapAdmin 应写入用户、密码凭证和工作区成员关系")
    void saveBootstrapAdmin_shouldPersistUserCredentialAndMembership() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcBootstrapAdminRepository repository = new JdbcBootstrapAdminRepository(jdbcTemplate);
        BootstrapAdminAccount account = new BootstrapAdminAccount(
                "user-1",
                "owner",
                "Owner",
                "{bcrypt}hash",
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                WorkspaceRole.WORKSPACE_OWNER,
                Instant.parse("2026-05-09T08:00:00Z"));
        Timestamp now = Timestamp.from(account.createdAt());
        when(jdbcTemplate.queryForObject(
                any(String.class),
                eq(String.class),
                eq("user-1"),
                eq("owner"),
                eq("Owner"),
                eq(now),
                eq(now)))
                .thenReturn("user-1");

        String userId = repository.saveBootstrapAdmin(account);

        assertEquals("user-1", userId);
        verify(jdbcTemplate).update(
                any(String.class),
                eq("user-1"),
                eq("{bcrypt}hash"),
                eq(now),
                eq(now),
                eq(now));
        verify(jdbcTemplate).update(
                any(String.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                eq("user-1"),
                eq("WORKSPACE_OWNER"),
                eq(now),
                eq(now));
    }
}
