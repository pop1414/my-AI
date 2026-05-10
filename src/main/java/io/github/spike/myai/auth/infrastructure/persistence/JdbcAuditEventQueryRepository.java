package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.AuditEventEntry;
import io.github.spike.myai.auth.domain.model.AuditEventPage;
import io.github.spike.myai.auth.domain.model.AuditEventSearchCriteria;
import io.github.spike.myai.auth.domain.port.AuditEventQueryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的审计事件查询仓储实现。
 * <p>
 * 使用 Spring {@link JdbcTemplate} 动态拼接 SQL 实现多维度组合筛选的分页查询。
 * 核心思路：基于固定前缀 SQL（BASE_COUNT_SQL / BASE_QUERY_SQL），
 * 按非空筛选条件动态追加 {@code AND ...} 子句，最后附加排序和分页。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>参数化查询（全部使用 {@code ?} 占位符），防止 SQL 注入</li>
 *   <li>筛选条件动态拼接，仅非 {@code null} 的条件参与过滤</li>
 *   <li>先查总数（COUNT），再查分页数据（LIMIT/OFFSET），确保前端分页准确</li>
 *   <li>按 {@code occurred_at DESC, audit_event_id DESC} 排序，利用主键索引保证分页稳定性</li>
 *   <li>使用内部 Record {@link FilterSql} 封装 SQL 与参数列表，简化动态拼接逻辑</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcAuditEventQueryRepository implements AuditEventQueryRepository {

    /**
     * 基础计数 SQL。
     * <p>
     * 查询符合工作区和筛选条件的审计事件总数，用于分页的 {@code total} 字段。
     */
    private static final String BASE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM audit_events
            WHERE workspace_id = ?
            """;

    /**
     * 基础查询 SQL。
     * <p>
     * 查询审计事件全量字段，{@code metadata::text} 用于 PostgreSQL 中 JSONB 到文本的转换。
     */
    private static final String BASE_QUERY_SQL = """
            SELECT audit_event_id,
                   workspace_id,
                   actor_user_id,
                   actor_username,
                   event_type,
                   target_type,
                   target_id,
                   outcome,
                   reason,
                   metadata::text AS metadata,
                   occurred_at
            FROM audit_events
            WHERE workspace_id = ?
            """;

    /** Spring JDBC 模板，用于执行所有 SQL 操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入 {@link JdbcTemplate}。
     *
     * @param jdbcTemplate Spring 自动配置的 JDBC 模板
     */
    public JdbcAuditEventQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按工作区与筛选条件查询审计事件分页结果。
     * <p>
     * 分两步执行：
     * <ol>
     *   <li>基于 {@link #BASE_COUNT_SQL} 动态拼接筛选条件，查询总记录数</li>
     *   <li>基于 {@link #BASE_QUERY_SQL} 动态拼接筛选条件 + 排序 + 分页，查询当前页数据</li>
     * </ol>
     * 注意：总数查询与数据查询使用完全相同的筛选条件，确保一致性。
     *
     * @param workspaceId 工作区 ID
     * @param criteria    查询条件，字段为 {@code null} 则跳过该维度过滤
     * @return 审计事件分页结果
     */
    @Override
    public AuditEventPage findPage(String workspaceId, AuditEventSearchCriteria criteria) {
        // Step 1: 拼接计数 SQL 并查询总记录数
        FilterSql countSql = buildFilterSql(BASE_COUNT_SQL, workspaceId, criteria);
        Long total = jdbcTemplate.queryForObject(countSql.sql(), Long.class, countSql.params().toArray());

        // Step 2: 拼接查询 SQL，附加排序和分页
        FilterSql querySql = buildFilterSql(BASE_QUERY_SQL, workspaceId, criteria);
        String orderedSql = querySql.sql() + """
                ORDER BY occurred_at DESC, audit_event_id DESC
                LIMIT ? OFFSET ?
                """;
        // 合并筛选参数与分页参数
        List<Object> params = new ArrayList<>(querySql.params());
        params.add(criteria.limit());
        params.add(criteria.offset());
        // 执行分页查询并映射为领域对象
        List<AuditEventEntry> items = jdbcTemplate.query(
                orderedSql,
                JdbcAuditEventQueryRepository::mapAuditEventEntry,
                params.toArray());

        // Step 3: 组装分页领域对象返回
        return new AuditEventPage(items, total == null ? 0L : total, criteria.limit(), criteria.offset());
    }

    /**
     * 基于基础 SQL 和筛选条件动态拼接 WHERE 子句。
     * <p>
     * 遍历 {@link AuditEventSearchCriteria} 的每个筛选维度，
     * 若不为 {@code null} 则追加对应的 {@code AND ... = ?} 子句并绑定参数。
     * <p>
     * 注意：{@code workspaceId} 总是第一个参数，后续按字段声明顺序追加。
     *
     * @param baseSql     基础 SQL（已包含 {@code WHERE workspace_id = ?}）
     * @param workspaceId 工作区 ID
     * @param criteria    搜索条件
     * @return 封装后的 SQL 与参数列表
     */
    private static FilterSql buildFilterSql(
            String baseSql,
            String workspaceId,
            AuditEventSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(baseSql);
        List<Object> params = new ArrayList<>();

        // workspaceId 总是第一个参数
        params.add(workspaceId);

        // 按字段逐一判断：非 null 则追加 AND 子句并绑定参数
        if (criteria.eventType() != null) {
            sql.append(" AND event_type = ?");
            params.add(criteria.eventType());
        }
        if (criteria.actorUserId() != null) {
            sql.append(" AND actor_user_id = ?");
            params.add(criteria.actorUserId());
        }
        if (criteria.targetType() != null) {
            sql.append(" AND target_type = ?");
            params.add(criteria.targetType());
        }
        if (criteria.targetId() != null) {
            sql.append(" AND target_id = ?");
            params.add(criteria.targetId());
        }
        if (criteria.outcome() != null) {
            sql.append(" AND outcome = ?");
            params.add(criteria.outcome());
        }
        if (criteria.occurredFrom() != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(criteria.occurredFrom()));
        }
        if (criteria.occurredTo() != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(Timestamp.from(criteria.occurredTo()));
        }

        // 末尾追加换行，便于与 ORDER BY/LIMIT 拼接
        sql.append('\n');
        return new FilterSql(sql.toString(), params);
    }

    /**
     * 将 JDBC {@link ResultSet} 当前行映射为 {@link AuditEventEntry} 领域对象。
     * <p>
     * 方法引用作为 {@link JdbcTemplate} 的 {@code RowMapper} 使用。
     * 注意：{@code metadata} 字段在数据库中是 JSONB 类型，
     * 通过 SQL 中的 {@code metadata::text} 转换为字符串后读取。
     *
     * @param rs     当前结果集行
     * @param rowNum 当前行号（0-based），由框架自动传入
     * @return 映射后的 {@link AuditEventEntry} 领域对象
     * @throws SQLException 当结果集读取失败时抛出
     */
    private static AuditEventEntry mapAuditEventEntry(ResultSet rs, int rowNum) throws SQLException {
        // 逐列读取并构造不可变领域对象
        return new AuditEventEntry(
                rs.getLong("audit_event_id"),
                rs.getString("workspace_id"),
                rs.getString("actor_user_id"),
                rs.getString("actor_username"),
                rs.getString("event_type"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("outcome"),
                rs.getString("reason"),
                rs.getString("metadata"),
                // Timestamp → Instant 转换
                rs.getTimestamp("occurred_at").toInstant());
    }

    /**
     * 内部 Record：封装动态拼接的 SQL 与其对应的参数列表。
     * <p>
     * 避免使用 {@code Map} 或 {@code Pair} 等通用容器，类型安全且语义清晰。
     */
    private record FilterSql(String sql, List<Object> params) {
    }
}
