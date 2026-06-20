package io.github.spike.myai.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
class JdbcWorkspaceMemberRepositoryTest {

    @Test
    @DisplayName("构造初始化不应执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcWorkspaceMemberRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("findActiveMembers 应仅查询用户与成员关系均为 ACTIVE 的记录")
    void findActiveMembers_shouldFilterActiveUsersAndMemberships() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcWorkspaceMemberRepository repository = new JdbcWorkspaceMemberRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("workspace-a"))).thenReturn(List.of());

        repository.findActiveMembers("workspace-a");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("workspace-a"));
        assertTrue(sqlCaptor.getValue().contains("FROM workspace_memberships wm"));
        assertTrue(sqlCaptor.getValue().contains("wm.workspace_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("wm.status = 'ACTIVE'"));
        assertTrue(sqlCaptor.getValue().contains("u.status = 'ACTIVE'"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY wm.created_at ASC, u.user_id ASC"));
    }

    @Test
    @DisplayName("updateWorkspaceRole 应只更新有效成员关系")
    void updateWorkspaceRole_shouldUpdateOnlyActiveMembership() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcWorkspaceMemberRepository repository = new JdbcWorkspaceMemberRepository(jdbcTemplate);
        Instant updatedAt = Instant.parse("2026-05-09T12:00:00Z");
        when(jdbcTemplate.update(
                any(String.class),
                eq("WORKSPACE_ADMIN"),
                eq(Timestamp.from(updatedAt)),
                eq("workspace-a"),
                eq("user-2")))
                .thenReturn(1);

        repository.updateWorkspaceRole("workspace-a", "user-2", WorkspaceRole.WORKSPACE_ADMIN, updatedAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("WORKSPACE_ADMIN"),
                eq(Timestamp.from(updatedAt)),
                eq("workspace-a"),
                eq("user-2"));
        assertTrue(sqlCaptor.getValue().contains("UPDATE workspace_memberships wm"));
        assertTrue(sqlCaptor.getValue().contains("wm.status = 'ACTIVE'"));
        assertTrue(sqlCaptor.getValue().contains("u.status = 'ACTIVE'"));
    }
}
