package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
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
 * 基于 JDBC 的工作区成员治理仓储实现。
 * <p>
 * 使用 Spring {@link JdbcTemplate} 直接执行 SQL，
 * 所有查询均通过 {@code workspace_memberships} 与 {@code users} 联表，
 * 并强制过滤双重 {@code ACTIVE} 状态以确保数据安全。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>不走 JPA/Hibernate，避免复杂映射和 N+1 问题</li>
 *   <li>使用 Text Block（{@code """}）编写 SQL，提升可读性</li>
 *   <li>更新操作通过 {@code EXISTS} 子查询二次校验用户状态，防止越权修改</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcWorkspaceMemberRepository implements WorkspaceMemberRepository {

    /** Spring JDBC 模板，用于执行所有 SQL 操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入 {@link JdbcTemplate}。
     *
     * @param jdbcTemplate Spring 自动配置的 JDBC 模板
     */
    public JdbcWorkspaceMemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询指定工作区下所有活跃成员。
     * <p>
     * SQL 逻辑：
     * <ol>
     *   <li>以 {@code workspace_memberships} 为主表，关联 {@code users} 获取用户详细信息</li>
     *   <li>过滤条件：成员关系状态 = {@code ACTIVE} 且用户状态 = {@code ACTIVE}</li>
     *   <li>排序：先按成员关系创建时间，再按用户 ID，保证分页场景下顺序稳定</li>
     * </ol>
     *
     * @param workspaceId 工作区 ID
     * @return 活跃成员列表，无结果时返回空列表
     */
    @Override
    public List<WorkspaceMember> findActiveMembers(String workspaceId) {
        // 使用 query 方法返回多条记录，自动映射为 WorkspaceMember 列表
        return jdbcTemplate.query(
                """
                        SELECT u.user_id,
                               u.username,
                               u.display_name,
                               wm.workspace_id,
                               wm.role AS workspace_role,
                               wm.status AS membership_status
                        FROM workspace_memberships wm
                        JOIN users u ON u.user_id = wm.user_id
                        WHERE wm.workspace_id = ?
                          AND wm.status = 'ACTIVE'
                          AND u.status = 'ACTIVE'
                        ORDER BY wm.created_at ASC, u.user_id ASC
                        """,
                JdbcWorkspaceMemberRepository::mapWorkspaceMember,
                workspaceId);
    }

    /**
     * 查询单个活跃成员。
     * <p>
     * 在 {@link #findActiveMembers} 基础上增加 {@code user_id} 精确匹配。
     * 使用 {@link JdbcTemplate#queryForObject} 期望恰好一条记录，
     * 若无结果则捕获 {@link EmptyResultDataAccessException} 返回 {@link Optional#empty()}。
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @return 包裹在 {@link Optional} 中的成员，不存在则为 {@link Optional#empty()}
     */
    @Override
    public Optional<WorkspaceMember> findActiveMember(String workspaceId, String userId) {
        try {
            // queryForObject 在无结果时会抛出 EmptyResultDataAccessException
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                            SELECT u.user_id,
                                   u.username,
                                   u.display_name,
                                   wm.workspace_id,
                                   wm.role AS workspace_role,
                                   wm.status AS membership_status
                            FROM workspace_memberships wm
                            JOIN users u ON u.user_id = wm.user_id
                            WHERE wm.workspace_id = ?
                              AND wm.user_id = ?
                              AND wm.status = 'ACTIVE'
                              AND u.status = 'ACTIVE'
                            """,
                    JdbcWorkspaceMemberRepository::mapWorkspaceMember,
                    workspaceId,
                    userId));
        } catch (EmptyResultDataAccessException ex) {
            // 查无此人时按接口契约返回空 Optional，而非抛异常
            return Optional.empty();
        }
    }

    /**
     * 更新指定活跃成员的工作区角色。
     * <p>
     * SQL 采用条件更新策略，包含三层防护：
     * <ol>
     *   <li>{@code workspace_id} 匹配：限定在当前工作区范围内操作</li>
     *   <li>{@code user_id} 匹配：精确锁定目标用户</li>
     *   <li>{@code status = 'ACTIVE'} + {@code EXISTS (SELECT ... users WHERE status = 'ACTIVE')}：
     *       双重校验成员关系和用户均为活跃状态，防止对已注销用户或已移除成员的误操作</li>
     * </ol>
     * 返回值通过 {@code > 0} 判断：受影响行数大于 0 表示更新成功，
     * 等于 0 表示未命中任何满足条件的行（成员已不存在或已非活跃）。
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户 ID
     * @param role        新角色枚举值，其 {@code name()} 将直接写入数据库
     * @param updatedAt   更新时间戳，转换为 {@link Timestamp} 后写入 {@code updated_at} 字段
     * @return {@code true} 更新成功；{@code false} 未命中目标行
     */
    @Override
    public boolean updateWorkspaceRole(String workspaceId, String userId, WorkspaceRole role, Instant updatedAt) {
        // 执行条件 UPDATE，受影响行数 > 0 表示成功
        return jdbcTemplate.update(
                """
                        UPDATE workspace_memberships wm
                        SET role = ?,
                            updated_at = ?
                        WHERE wm.workspace_id = ?
                          AND wm.user_id = ?
                          AND wm.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1
                              FROM users u
                              WHERE u.user_id = wm.user_id
                                AND u.status = 'ACTIVE'
                          )
                        """,
                // 参数绑定：角色名、时间戳、工作区 ID、用户 ID
                role.name(),
                Timestamp.from(updatedAt),
                workspaceId,
                userId) > 0;
    }

    /**
     * 将 JDBC {@link ResultSet} 当前行映射为 {@link WorkspaceMember} 领域对象。
     * <p>
     * 方法引用 {@code JdbcWorkspaceMemberRepository::mapWorkspaceMember}
     * 作为 {@link JdbcTemplate} 的 {@code RowMapper} 使用。
     * 注意：{@code rowNum} 参数由框架传入，此处未使用但不影响映射逻辑。
     *
     * @param rs     当前结果集行
     * @param rowNum 当前行号（0-based），由框架自动传入
     * @return 映射后的 {@link WorkspaceMember} 领域对象
     * @throws SQLException 当结果集读取失败时抛出
     */
    private static WorkspaceMember mapWorkspaceMember(ResultSet rs, int rowNum) throws SQLException {
        // 逐列读取并构造不可变领域对象
        return new WorkspaceMember(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("workspace_id"),
                // 将数据库中角色字符串还原为枚举值
                WorkspaceRole.valueOf(rs.getString("workspace_role")),
                rs.getString("membership_status"));
    }
}
