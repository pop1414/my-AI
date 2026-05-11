package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 托管账号仓储 JDBC 实现。
 *
 * <p>基于 Spring {@link JdbcTemplate} 实现 {@link ManagedAccountRepository} 端口，
 * 所有写操作均以工作区为隔离边界，通过子查询校验用户归属，防止越权操作。
 *
 * <p>涉及的表：
 * <ul>
 *   <li>{@code users} — 用户基础信息</li>
 *   <li>{@code local_credentials} — 本地凭据（密码哈希）</li>
 *   <li>{@code workspace_memberships} — 工作区成员关系</li>
 *   <li>{@code login_lock_states} — 登录锁定状态</li>
 * </ul>
 */
@Repository
public class JdbcManagedAccountRepository implements ManagedAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数注入 JdbcTemplate。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcManagedAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询指定工作区下所有托管账号。
     *
     * <p>三表关联查询：
     * <ul>
     *   <li>{@code workspace_memberships} 为主表，按工作区过滤</li>
     *   <li>{@code JOIN users} 获取用户基础信息</li>
     *   <li>{@code LEFT JOIN login_lock_states} 获取锁定状态（无记录时默认 0 / null）</li>
     * </ul>
     * 结果按成员创建时间升序、用户 ID 升序排列。
     */
    @Override
    public List<ManagedAccount> findWorkspaceAccounts(String workspaceId) {
        return jdbcTemplate.query(
                """
                        SELECT u.user_id,
                               u.username,
                               u.display_name,
                               u.status AS user_status,
                               wm.workspace_id,
                               wm.role AS workspace_role,
                               wm.status AS membership_status,
                               COALESCE(lls.failed_login_count, 0) AS failed_login_count,
                               lls.locked_until
                        FROM workspace_memberships wm
                        JOIN users u ON u.user_id = wm.user_id
                        LEFT JOIN login_lock_states lls ON lls.user_id = u.user_id
                        WHERE wm.workspace_id = ?
                        ORDER BY wm.created_at ASC, u.user_id ASC
                        """,
                JdbcManagedAccountRepository::mapManagedAccount,
                workspaceId);
    }

    /**
     * 按工作区和用户 ID 查询单个托管账号。
     *
     * <p>SQL 结构与列表查询一致，额外增加 {@code wm.user_id = ?} 条件精确定位。
     * 当查询无结果时捕获 {@link EmptyResultDataAccessException} 返回空 Optional。
     */
    @Override
    public Optional<ManagedAccount> findWorkspaceAccount(String workspaceId, String userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                            SELECT u.user_id,
                                   u.username,
                                   u.display_name,
                                   u.status AS user_status,
                                   wm.workspace_id,
                                   wm.role AS workspace_role,
                                   wm.status AS membership_status,
                                   COALESCE(lls.failed_login_count, 0) AS failed_login_count,
                                   lls.locked_until
                            FROM workspace_memberships wm
                            JOIN users u ON u.user_id = wm.user_id
                            LEFT JOIN login_lock_states lls ON lls.user_id = u.user_id
                            WHERE wm.workspace_id = ?
                              AND wm.user_id = ?
                            """,
                    JdbcManagedAccountRepository::mapManagedAccount,
                    workspaceId,
                    userId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    /**
     * 判断用户名是否已存在于 {@code users} 表。
     *
     * <p>使用 {@code COUNT} 查询，效率高于返回全量字段的 SELECT。
     */
    @Override
    public boolean existsUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class,
                username);
        return count != null && count > 0;
    }

    /**
     * 创建托管账号。
     *
     * <p>在同一业务事务中依次执行三次 INSERT：
     * <ol>
     *   <li>向 {@code users} 表插入用户基础信息，状态默认为 ACTIVE</li>
     *   <li>向 {@code local_credentials} 表插入凭据，算法固定为 bcrypt</li>
     *   <li>向 {@code workspace_memberships} 表插入成员关系，状态默认为 ACTIVE</li>
     * </ol>
     * 创建完成后通过 {@link #findWorkspaceAccount} 回查确认数据一致性。
     *
     * @return 创建成功的托管账号读模型
     * @throws IllegalStateException 如果创建后回查失败（数据不一致）
     */
    @Override
    public ManagedAccount createAccount(
            String workspaceId,
            String username,
            String displayName,
            String passwordHash,
            WorkspaceRole workspaceRole,
            Instant now) {
        // 生成 UUID 作为用户唯一标识
        String userId = UUID.randomUUID().toString();
        Timestamp timestamp = Timestamp.from(now);

        // 1. 插入用户基础信息
        jdbcTemplate.update(
                """
                        INSERT INTO users (user_id, username, display_name, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                        """,
                userId,
                username,
                displayName,
                timestamp,
                timestamp);

        // 2. 插入本地凭据（密码哈希 + bcrypt 算法标识）
        jdbcTemplate.update(
                """
                        INSERT INTO local_credentials
                          (user_id, password_hash, password_algo, password_updated_at, created_at, updated_at)
                        VALUES (?, ?, 'bcrypt', ?, ?, ?)
                        """,
                userId,
                passwordHash,
                timestamp,
                timestamp,
                timestamp);

        // 3. 插入工作区成员关系
        jdbcTemplate.update(
                """
                        INSERT INTO workspace_memberships
                          (workspace_id, user_id, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                        """,
                workspaceId,
                userId,
                workspaceRole.name(),
                timestamp,
                timestamp);

        // 4. 回查确认数据一致性，如果创建失败则抛出异常
        return findWorkspaceAccount(workspaceId, userId)
                .orElseThrow(() -> new IllegalStateException("created managed account not found"));
    }

    /**
     * 更新用户状态。
     *
     * <p>使用子查询 {@code EXISTS} 校验用户是否属于指定工作区，
     * 防止越权修改其他工作区的用户。仅当匹配到记录时才执行更新。
     *
     * @return true 表示更新成功（影响行数 > 0），false 表示未找到匹配记录
     */
    @Override
    public boolean updateUserStatus(String workspaceId, String userId, String userStatus, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE users u
                        SET status = ?,
                            updated_at = ?
                        WHERE u.user_id = ?
                          AND EXISTS (
                              SELECT 1
                              FROM workspace_memberships wm
                              WHERE wm.workspace_id = ?
                                AND wm.user_id = u.user_id
                          )
                        """,
                userStatus,
                Timestamp.from(updatedAt),
                userId,
                workspaceId) > 0;
    }

    /**
     * 重置密码并清除锁定状态。
     *
     * <p>分为两步操作：
     * <ol>
     *   <li>更新 {@code local_credentials} 表：覆盖密码哈希和算法标识，
     *       同样通过子查询校验工作区归属</li>
     *   <li>UPSERT {@code login_lock_states} 表：将 failedLoginCount 归零、
     *       lockedUntil 置空。使用 {@code ON CONFLICT ... DO UPDATE} 语法
     *       兼容 PostgreSQL 的 upsert 语义</li>
     * </ol>
     *
     * @return true 表示密码更新成功（凭据更新影响行数 > 0）
     */
    @Override
    public boolean resetPassword(String workspaceId, String userId, String passwordHash, Instant updatedAt) {
        Timestamp timestamp = Timestamp.from(updatedAt);

        // 1. 更新凭据：覆盖密码哈希，通过子查询校验工作区归属
        int credentialUpdated = jdbcTemplate.update(
                """
                        UPDATE local_credentials lc
                        SET password_hash = ?,
                            password_algo = 'bcrypt',
                            password_updated_at = ?,
                            updated_at = ?
                        WHERE lc.user_id = ?
                          AND EXISTS (
                              SELECT 1
                              FROM workspace_memberships wm
                              WHERE wm.workspace_id = ?
                                AND wm.user_id = lc.user_id
                          )
                        """,
                passwordHash,
                timestamp,
                timestamp,
                userId,
                workspaceId);

        // 如果凭据未更新（用户不属于该工作区），直接返回失败
        if (credentialUpdated <= 0) {
            return false;
        }

        // 2. 清除锁定状态：UPSERT 语义，无记录则插入、有记录则清零
        jdbcTemplate.update(
                """
                        INSERT INTO login_lock_states
                          (user_id, failed_login_count, locked_until, last_failed_at, last_login_at, updated_at)
                        VALUES (?, 0, NULL, NULL, NULL, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET failed_login_count = 0,
                            locked_until = NULL,
                            updated_at = EXCLUDED.updated_at
                        """,
                userId,
                timestamp);
        return true;
    }

    /**
     * 将工作区成员关系标记为 INACTIVE。
     *
     * <p>仅当成员当前状态为 ACTIVE 时才执行更新，实现幂等性。
     * 如果成员已经是 INACTIVE 状态，则影响行数为 0。
     *
     * @return true 表示成功将 ACTIVE 转为 INACTIVE，false 表示已是 INACTIVE 或未找到记录
     */
    @Override
    public boolean deactivateMembership(String workspaceId, String userId, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE workspace_memberships
                        SET status = 'INACTIVE',
                            updated_at = ?
                        WHERE workspace_id = ?
                          AND user_id = ?
                          AND status = 'ACTIVE'
                        """,
                Timestamp.from(updatedAt),
                workspaceId,
                userId) > 0;
    }

    /**
     * 将 JDBC 结果集映射为 {@link ManagedAccount} 领域模型。
     *
     * <p>RowMapper 实现，由 JdbcTemplate 在每行数据上回调。
     * 注意 {@code locked_until} 可能为 null（未锁定状态）。
     *
     * @param rs     当前行的结果集
     * @param rowNum 行号（从 0 开始）
     * @return 托管账号读模型
     * @throws SQLException 如果结果集读取失败
     */
    private static ManagedAccount mapManagedAccount(ResultSet rs, int rowNum) throws SQLException {
        return new ManagedAccount(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("user_status"),
                rs.getString("workspace_id"),
                // 将数据库字符串映射为 WorkspaceRole 枚举
                WorkspaceRole.valueOf(rs.getString("workspace_role")),
                rs.getString("membership_status"),
                rs.getInt("failed_login_count"),
                toInstant(rs.getTimestamp("locked_until")));
    }

    /**
     * 将 {@link Timestamp} 转换为 {@link Instant}，处理 null 值。
     *
     * @param timestamp JDBC 时间戳，可能为 null
     * @return 对应的 Instant，null 输入返回 null
     */
    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
