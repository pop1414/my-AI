package io.github.spike.myai.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
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
class JdbcKnowledgeBaseGrantManagementRepositoryTest {

    @Test
    @DisplayName("构造初始化不应执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcKnowledgeBaseGrantManagementRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("findActiveGrants 应只返回 ACTIVE 且成员仍有效的 grant")
    void findActiveGrants_shouldFilterActiveGrantAndActiveMember() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseGrantManagementRepository repository = new JdbcKnowledgeBaseGrantManagementRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("default"), eq("kb-1"))).thenReturn(List.of());

        repository.findActiveGrants("default", "kb-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("default"), eq("kb-1"));
        assertTrue(sqlCaptor.getValue().contains("FROM knowledge_base_grants g"));
        assertTrue(sqlCaptor.getValue().contains("JOIN workspace_memberships wm"));
        assertTrue(sqlCaptor.getValue().contains("g.status = 'ACTIVE'"));
        assertTrue(sqlCaptor.getValue().contains("u.status = 'ACTIVE'"));
        assertTrue(sqlCaptor.getValue().contains("wm.status = 'ACTIVE'"));
    }

    @Test
    @DisplayName("saveGrant 应通过 UPSERT 维持单一真源")
    void saveGrant_shouldUseUpsert() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseGrantManagementRepository repository = new JdbcKnowledgeBaseGrantManagementRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-05-09T12:00:00Z");

        repository.saveGrant("default", "kb-1", "user-2", KnowledgeBaseRole.KB_MANAGER, now);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("default"),
                eq("kb-1"),
                eq("user-2"),
                eq("KB_MANAGER"),
                eq(Timestamp.from(now)),
                eq(Timestamp.from(now)));
        assertTrue(sqlCaptor.getValue().contains("ON CONFLICT (workspace_id, kb_id, user_id) DO UPDATE"));
        assertTrue(sqlCaptor.getValue().contains("status = 'ACTIVE'"));
    }

    @Test
    @DisplayName("disableGrant 应将 ACTIVE grant 更新为 DISABLED")
    void disableGrant_shouldDisableActiveGrant() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseGrantManagementRepository repository = new JdbcKnowledgeBaseGrantManagementRepository(jdbcTemplate);
        Instant now = Instant.parse("2026-05-09T12:00:00Z");
        when(jdbcTemplate.update(any(String.class), eq(Timestamp.from(now)), eq("default"), eq("kb-1"), eq("user-2")))
                .thenReturn(1);

        repository.disableGrant("default", "kb-1", "user-2", now);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq(Timestamp.from(now)), eq("default"), eq("kb-1"), eq("user-2"));
        assertTrue(sqlCaptor.getValue().contains("SET status = 'DISABLED'"));
        assertTrue(sqlCaptor.getValue().contains("AND status = 'ACTIVE'"));
    }
}
