package io.github.spike.myai.auth.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.domain.model.LoginFailureState;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcLocalAccountRepositoryTest {

    @Test
    @DisplayName("构造初始化不应执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcLocalAccountRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("recordFailedLogin 应将 locked_until 显式转换为 timestamptz")
    void recordFailedLogin_shouldCastLockedUntilToTimestamptz() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcLocalAccountRepository repository = new JdbcLocalAccountRepository(jdbcTemplate);
        Instant failedAt = Instant.parse("2026-05-10T05:45:00Z");
        Instant lockUntil = failedAt.plusSeconds(600);
        Timestamp failedAtTimestamp = Timestamp.from(failedAt);
        Timestamp lockUntilTimestamp = Timestamp.from(lockUntil);

        when(jdbcTemplate.queryForObject(
                any(String.class),
                any(RowMapper.class),
                eq("user-1"),
                eq(3),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp),
                eq(3),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<LoginFailureState> rowMapper =
                            (RowMapper<LoginFailureState>) invocation.getArgument(1);
                    ResultSet rs = Mockito.mock(ResultSet.class);
                    when(rs.getInt("failed_login_count")).thenReturn(2);
                    when(rs.getTimestamp("locked_until")).thenReturn(lockUntilTimestamp);
                    return rowMapper.mapRow(rs, 0);
                });

        LoginFailureState state = repository.recordFailedLogin("user-1", failedAt, 3, lockUntil);

        assertEquals(2, state.failedLoginCount());
        assertEquals(lockUntil, state.lockedUntil());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq("user-1"),
                eq(3),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp),
                eq(3),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp));
        assertTrue(sqlCaptor.getValue().contains("CAST(? AS TIMESTAMPTZ)"));
        assertTrue(sqlCaptor.getValue().contains("NULL::TIMESTAMPTZ"));
    }

    @Test
    @DisplayName("recordFailedLogin 未触发锁定时应返回空 lockedUntil")
    void recordFailedLogin_shouldHandleNullLockedUntilFromResult() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcLocalAccountRepository repository = new JdbcLocalAccountRepository(jdbcTemplate);
        Instant failedAt = Instant.parse("2026-05-10T05:45:00Z");
        Instant lockUntil = failedAt.plusSeconds(600);
        Timestamp failedAtTimestamp = Timestamp.from(failedAt);
        Timestamp lockUntilTimestamp = Timestamp.from(lockUntil);

        when(jdbcTemplate.queryForObject(
                any(String.class),
                any(RowMapper.class),
                eq("user-2"),
                eq(5),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp),
                eq(5),
                eq(lockUntilTimestamp),
                eq(failedAtTimestamp),
                eq(failedAtTimestamp)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<LoginFailureState> rowMapper =
                            (RowMapper<LoginFailureState>) invocation.getArgument(1);
                    ResultSet rs = Mockito.mock(ResultSet.class);
                    when(rs.getInt("failed_login_count")).thenReturn(1);
                    when(rs.getTimestamp("locked_until")).thenReturn(null);
                    return rowMapper.mapRow(rs, 0);
                });

        LoginFailureState state = repository.recordFailedLogin("user-2", failedAt, 5, lockUntil);

        assertEquals(1, state.failedLoginCount());
        assertNull(state.lockedUntil());
    }
}
