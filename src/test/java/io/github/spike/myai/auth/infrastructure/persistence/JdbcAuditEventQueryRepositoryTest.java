package io.github.spike.myai.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.domain.model.AuditEventSearchCriteria;
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
class JdbcAuditEventQueryRepositoryTest {

    @Test
    @DisplayName("构造初始化不应执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcAuditEventQueryRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("findPage 应拼装筛选条件、排序与分页")
    void findPage_shouldBuildFilterAndPaginationSql() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcAuditEventQueryRepository repository = new JdbcAuditEventQueryRepository(jdbcTemplate);
        AuditEventSearchCriteria criteria = new AuditEventSearchCriteria(
                "DOCUMENT_GRANT_UPSERTED",
                "user-admin",
                "alice",
                "DOCUMENT_GRANT",
                "doc-1:user-2",
                "SUCCESS",
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-10T23:59:59Z"),
                20,
                0);
        when(jdbcTemplate.queryForObject(any(String.class), Mockito.eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findPage("default", criteria);

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSqlCaptor.capture(), Mockito.eq(Long.class), any(Object[].class));
        assertTrue(countSqlCaptor.getValue().contains("FROM audit_events"));
        assertTrue(countSqlCaptor.getValue().contains("workspace_id = ?"));
        assertTrue(countSqlCaptor.getValue().contains("event_type = ?"));
        assertTrue(countSqlCaptor.getValue().contains("actor_user_id = ?"));
        assertTrue(countSqlCaptor.getValue().contains("actor_user_id ILIKE ?"));
        assertTrue(countSqlCaptor.getValue().contains("actor_username ILIKE ?"));
        assertTrue(countSqlCaptor.getValue().contains("target_type = ?"));
        assertTrue(countSqlCaptor.getValue().contains("target_id = ?"));
        assertTrue(countSqlCaptor.getValue().contains("outcome = ?"));
        assertTrue(countSqlCaptor.getValue().contains("occurred_at >= ?"));
        assertTrue(countSqlCaptor.getValue().contains("occurred_at <= ?"));

        ArgumentCaptor<String> querySqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(querySqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(querySqlCaptor.getValue().contains("metadata::text AS metadata"));
        assertTrue(querySqlCaptor.getValue().contains("ORDER BY occurred_at DESC, audit_event_id DESC"));
        assertTrue(querySqlCaptor.getValue().contains("LIMIT ? OFFSET ?"));
    }
}
