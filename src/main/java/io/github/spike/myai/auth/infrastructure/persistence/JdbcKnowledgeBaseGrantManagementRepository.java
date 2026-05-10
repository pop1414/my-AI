package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的知识库授权治理仓储实现。
 * <p>
 * 使用 Spring {@link JdbcTemplate} 直接执行 SQL，
 * 所有查询均通过 {@code knowledge_base_grants} 与 {@code users} 联表，
 * 列表查询额外关联 {@code workspace_memberships} 确保仅返回活跃工作区成员的授权。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>SQL 提取为静态常量（Text Block），提升可读性和可测试性</li>
 *   <li>授予操作使用 {@code INSERT ... ON CONFLICT DO UPDATE} 实现原子 Upsert</li>
 *   <li>回收操作为软删除（{@code SET status = 'DISABLED'}），保留审计追溯能力</li>
 *   <li>通过 {@code ON CONFLICT} 利用数据库唯一约束保证并发安全</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcKnowledgeBaseGrantManagementRepository implements KnowledgeBaseGrantManagementRepository {

    /**
     * 查询活跃授权列表 SQL。
     * <p>
     * 三表联查（grants + users + workspace_memberships），
     * 三重 ACTIVE 过滤（授权状态 + 用户状态 + 成员关系状态），
     * 按创建时间和用户 ID 升序排列。
     */
    private static final String FIND_ACTIVE_GRANTS_SQL = """
            SELECT g.workspace_id,
                   g.kb_id,
                   g.user_id,
                   u.username,
                   u.display_name,
                   g.role,
                   g.status
            FROM knowledge_base_grants g
            JOIN users u ON u.user_id = g.user_id
            JOIN workspace_memberships wm
              ON wm.workspace_id = g.workspace_id
             AND wm.user_id = g.user_id
            WHERE g.workspace_id = ?
              AND g.kb_id = ?
              AND g.status = 'ACTIVE'
              AND u.status = 'ACTIVE'
              AND wm.status = 'ACTIVE'
            ORDER BY g.created_at ASC, g.user_id ASC
            """;

    /**
     * 查询单条活跃授权 SQL。
     * <p>
     * 两表联查（grants + users），通过工作区、知识库、用户三维度定位，
     * 仅匹配授权状态为 ACTIVE 的记录。
     */
    private static final String FIND_ACTIVE_GRANT_SQL = """
            SELECT g.workspace_id,
                   g.kb_id,
                   g.user_id,
                   u.username,
                   u.display_name,
                   g.role,
                   g.status
            FROM knowledge_base_grants g
            JOIN users u ON u.user_id = g.user_id
            WHERE g.workspace_id = ?
              AND g.kb_id = ?
              AND g.user_id = ?
              AND g.status = 'ACTIVE'
            """;

    /**
     * Upsert 授权 SQL。
     * <p>
     * 使用 PostgreSQL {@code INSERT ... ON CONFLICT DO UPDATE} 语法，
     * 在 {@code (workspace_id, kb_id, user_id)} 唯一约束冲突时更新角色和状态。
     * 首次插入时 {@code created_at} 和 {@code updated_at} 均为当前时间戳。
     */
    private static final String UPSERT_GRANT_SQL = """
            INSERT INTO knowledge_base_grants
              (workspace_id, kb_id, user_id, role, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            ON CONFLICT (workspace_id, kb_id, user_id) DO UPDATE
            SET role = EXCLUDED.role,
                status = 'ACTIVE',
                updated_at = EXCLUDED.updated_at
            """;

    /**
     * 回收授权 SQL（软删除）。
     * <p>
     * 将匹配的 ACTIVE 授权记录状态更新为 DISABLED，
     * 仅更新 {@code updated_at} 时间戳，不执行物理删除。
     */
    private static final String DISABLE_GRANT_SQL = """
            UPDATE knowledge_base_grants
            SET status = 'DISABLED',
                updated_at = ?
            WHERE workspace_id = ?
              AND kb_id = ?
              AND user_id = ?
              AND status = 'ACTIVE'
            """;

    /** Spring JDBC 模板，用于执行所有 SQL 操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入 {@link JdbcTemplate}。
     *
     * @param jdbcTemplate Spring 自动配置的 JDBC 模板
     */
    public JdbcKnowledgeBaseGrantManagementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询指定知识库下所有活跃授权记录。
     * <p>
     * 使用预编译的 {@link #FIND_ACTIVE_GRANTS_SQL}，
     * 返回结果自动映射为 {@link KnowledgeBaseGrant} 列表。
     *
     * @param workspaceId 工作区 ID
     * @param kbId        知识库 ID
     * @return 活跃授权列表，无结果时返回空列表
     */
    @Override
    public List<KnowledgeBaseGrant> findActiveGrants(String workspaceId, String kbId) {
        // 使用预编译 SQL 常量，通过 RowMapper 自动映射
        return jdbcTemplate.query(
                FIND_ACTIVE_GRANTS_SQL,
                JdbcKnowledgeBaseGrantManagementRepository::mapKnowledgeBaseGrant,
                workspaceId,
                kbId);
    }

    /**
     * 查询单条活跃授权记录。
     * <p>
     * 使用 {@link JdbcTemplate#queryForObject} 期望恰好一条记录，
     * 若无结果则捕获 {@link EmptyResultDataAccessException} 返回 {@link Optional#empty()}。
     *
     * @param workspaceId 工作区 ID
     * @param kbId        知识库 ID
     * @param userId      用户 ID
     * @return 活跃授权，不存在则为 {@link Optional#empty()}
     */
    @Override
    public Optional<KnowledgeBaseGrant> findActiveGrant(String workspaceId, String kbId, String userId) {
        try {
            // queryForObject 在无结果时抛出 EmptyResultDataAccessException
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    FIND_ACTIVE_GRANT_SQL,
                    JdbcKnowledgeBaseGrantManagementRepository::mapKnowledgeBaseGrant,
                    workspaceId,
                    kbId,
                    userId));
        } catch (EmptyResultDataAccessException ex) {
            // 查无记录时按接口契约返回空 Optional
            return Optional.empty();
        }
    }

    /**
     * 授予或更新知识库授权（Upsert）。
     * <p>
     * 使用 {@link #UPSERT_GRANT_SQL} 执行原子 Upsert：
     * <ul>
     *   <li>不存在则插入新记录，状态设为 {@code ACTIVE}</li>
     *   <li>已存在（唯一约束冲突）则更新角色为 {@code ACTIVE} 并刷新时间戳</li>
     * </ul>
     *
     * @param workspaceId 工作区 ID
     * @param kbId        知识库 ID
     * @param userId      用户 ID
     * @param role        知识库角色枚举
     * @param updatedAt   操作时间戳（同时用作 {@code created_at} 和 {@code updated_at}）
     */
    @Override
    public void saveGrant(String workspaceId, String kbId, String userId, KnowledgeBaseRole role, Instant updatedAt) {
        // Instant → Timestamp 转换
        Timestamp timestamp = Timestamp.from(updatedAt);
        // 执行 Upsert：同一个 timestamp 用于 created_at 和 updated_at
        jdbcTemplate.update(
                UPSERT_GRANT_SQL,
                workspaceId,
                kbId,
                userId,
                role.name(),
                timestamp,
                timestamp);
    }

    /**
     * 回收知识库授权（软删除）。
     * <p>
     * 使用 {@link #DISABLE_GRANT_SQL} 将匹配的 {@code ACTIVE} 授权记录状态更新为 {@code DISABLED}。
     * 通过 {@code > 0} 判断受影响行数：等于 0 表示未命中任何活跃授权。
     *
     * @param workspaceId 工作区 ID
     * @param kbId        知识库 ID
     * @param userId      用户 ID
     * @param updatedAt   操作时间戳
     * @return {@code true} 成功禁用；{@code false} 未命中目标行
     */
    @Override
    public boolean disableGrant(String workspaceId, String kbId, String userId, Instant updatedAt) {
        // 执行条件 UPDATE，受影响行数 > 0 表示成功
        return jdbcTemplate.update(
                DISABLE_GRANT_SQL,
                Timestamp.from(updatedAt),
                workspaceId,
                kbId,
                userId) > 0;
    }

    /**
     * 将 JDBC {@link ResultSet} 当前行映射为 {@link KnowledgeBaseGrant} 领域对象。
     * <p>
     * 方法引用作为 {@link JdbcTemplate} 的 {@code RowMapper} 使用。
     * {@code rowNum} 参数由框架传入，此处未使用。
     *
     * @param rs     当前结果集行
     * @param rowNum 当前行号（0-based），由框架自动传入
     * @return 映射后的 {@link KnowledgeBaseGrant} 领域对象
     * @throws SQLException 当结果集读取失败时抛出
     */
    private static KnowledgeBaseGrant mapKnowledgeBaseGrant(ResultSet rs, int rowNum) throws SQLException {
        // 逐列读取并构造不可变领域对象
        return new KnowledgeBaseGrant(
                rs.getString("workspace_id"),
                rs.getString("kb_id"),
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                // 将数据库中角色字符串还原为枚举值
                KnowledgeBaseRole.valueOf(rs.getString("role")),
                rs.getString("status"));
    }
}
