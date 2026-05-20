package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 JDBC 的审计事件仓储实现。
 *
 * <p>实现 {@link AuditEventRepository} 端口，使用 {@link JdbcTemplate}
 * 将审计事件持久化到 PostgreSQL 的 {@code audit_events} 表中。
 *
 * <p>审计事件为只追加（append-only）数据，本实现仅提供 {@code INSERT} 操作，
 * 不支持修改或删除，确保审计日志的不可篡改性。
 *
 * <p>注意事项：
 * <ul>
 *   <li>{@code metadata} 字段使用 PostgreSQL {@code jsonb} 类型，
 *       插入时若为空或空白则默认写入 {@code "{}"}；</li>
 *   <li>{@code occurred_at} 使用 {@link java.sql.Timestamp} 类型映射，
 *       精度为微秒级。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcAuditEventRepository implements AuditEventRepository {

    /** Spring JDBC 模板，用于执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存一条审计事件到数据库。
     *
     * <p>执行简单的 INSERT 语句，将审计事件的所有字段写入
     * {@code audit_events} 表。审计写入使用独立事务提交，避免主业务事务
     * 回滚时丢失失败审计记录。{@code metadata} 字段作为 JSONB 类型处理，
     * 使用 PostgreSQL 的 {@code ?::jsonb} 类型转换语法。
     *
     * @param event 待持久化的审计事件
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event) {
        // 执行 INSERT 语句，将审计事件的各个字段写入 audit_events 表
        jdbcTemplate.update(
                """
                        INSERT INTO audit_events
                          (workspace_id, actor_user_id, actor_username, event_type,
                           target_type, target_id, outcome, reason, metadata, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                        """,
                event.workspaceId(),
                event.actorUserId(),
                event.actorUsername(),
                event.eventType(),
                event.targetType(),
                event.targetId(),
                event.outcome(),
                event.reason(),
                // metadata 为空或空白时使用默认空 JSON 对象 "{}"
                event.metadata() == null || event.metadata().isBlank() ? "{}" : event.metadata(),
                // Instant → Timestamp 转换，适配 JDBC 时间类型
                Timestamp.from(event.occurredAt()));
    }
}
