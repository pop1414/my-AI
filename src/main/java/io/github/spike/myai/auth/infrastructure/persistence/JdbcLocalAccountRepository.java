package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.LoginFailureState;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.LocalAccountRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的本地账户仓储实现。
 *
 * <p>实现 {@link LocalAccountRepository} 端口，使用 {@link JdbcTemplate}
 * 对 PostgreSQL 执行账户查询与登录状态更新的 SQL 操作。
 *
 * <p>涉及的表：
 * <ul>
 *   <li>{@code users} —— 用户基础信息；</li>
 *   <li>{@code local_credentials} —— 本地密码凭证（BCrypt 哈希）；</li>
 *   <li>{@code workspace_memberships} —— 工作空间成员资格；</li>
 *   <li>{@code login_lock_states} —— 登录锁定状态（失败计数、锁定截止时间）。</li>
 * </ul>
 *
 * <p>并发安全：所有写操作通过 PostgreSQL 的 {@code INSERT ... ON CONFLICT ... DO UPDATE}
 * (UPSERT) 实现，利用行锁保证原子性，避免读写竞争条件。
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcLocalAccountRepository implements LocalAccountRepository {

    /** Spring JDBC 模板，用于执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcLocalAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按用户名查询登录账户。
     *
     * <p>执行四表联查（users + local_credentials + workspace_memberships +
     * login_lock_states），一次性加载登录所需的全部信息：
     * <ul>
     *   <li>{@code users} —— 用户 ID、用户名、展示名称、用户状态；</li>
     *   <li>{@code local_credentials} —— 密码哈希（INNER JOIN，确保只有本地账户可登录）；</li>
     *   <li>{@code workspace_memberships} —— 工作空间角色和成员资格状态
     *       （LEFT JOIN，按默认工作空间 ID 过滤）；</li>
     *   <li>{@code login_lock_states} —— 失败计数和锁定截止时间
     *       （LEFT JOIN，使用 COALESCE 默认为 0 / null）。</li>
     * </ul>
     *
     * @param username 用户名
     * @return 包含完整登录信息的账户，若未找到则为 {@link Optional#empty()}
     */
    @Override
    public Optional<LoginAccount> findByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                            SELECT u.user_id,
                                   u.username,
                                   u.display_name,
                                   u.status AS user_status,
                                   c.password_hash,
                                   wm.workspace_id,
                                   wm.role AS workspace_role,
                                   wm.status AS membership_status,
                                   COALESCE(lls.failed_login_count, 0) AS failed_login_count,
                                   lls.locked_until
                            FROM users u
                            -- INNER JOIN：仅本地账户（拥有密码凭证的账户）可登录
                            JOIN local_credentials c ON c.user_id = u.user_id
                            -- LEFT JOIN：工作空间成员资格可能不存在（用户未加入任何工作空间）
                            LEFT JOIN workspace_memberships wm
                              ON wm.user_id = u.user_id
                             AND wm.workspace_id = ?
                            -- LEFT JOIN：首次登录时可能无锁定状态记录
                            LEFT JOIN login_lock_states lls ON lls.user_id = u.user_id
                            WHERE u.username = ?
                            """,
                    (rs, rowNum) -> mapLoginAccount(rs),
                    // 查询参数1：默认工作空间 ID（用于 workspace_memberships 过滤）
                    WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                    // 查询参数2：用户名
                    username));
        } catch (EmptyResultDataAccessException ex) {
            // 查询无结果，返回空 Optional（区分于其他异常）
            return Optional.empty();
        }
    }

    /**
     * 记录一次登录失败并返回最新锁定状态。
     *
     * <p>使用 PostgreSQL 的 UPSERT 语法（{@code INSERT ... ON CONFLICT ... DO UPDATE}），
     * 在单条 SQL 中原子性地完成以下逻辑：
     *
     * <p><strong>INSERT 分支（首次失败）：</strong>
     * <ul>
     *   <li>失败计数设为 1；</li>
     *   <li>若 {@code maxFailedAttempts <= 1}，立即锁定（设置 locked_until）；
     *       否则暂不锁定（locked_until = NULL）。</li>
     * </ul>
     *
     * <p><strong>UPDATE 分支（重复失败）：</strong>
     * <ul>
     *   <li>失败计数 +1；</li>
     *   <li>若新计数 >= 阈值，更新锁定截止时间；否则保持原有锁定状态不变。</li>
     * </ul>
     *
     * <p>{@code RETURNING} 子句确保调用方能获取最新的失败计数和锁定状态，
     * 无需额外查询。
     *
     * @param userId            用户 ID
     * @param failedAt          失败时间戳
     * @param maxFailedAttempts 触发锁定的最大失败次数阈值
     * @param lockUntil         计算好的锁定截止时间
     * @return 最新的登录失败状态（失败次数 + 锁定截止时间）
     */
    @Override
    public LoginFailureState recordFailedLogin(
            String userId,
            Instant failedAt,
            int maxFailedAttempts,
            Instant lockUntil) {
        // Instant → Timestamp 转换，适配 JDBC
        Timestamp failedAtTimestamp = Timestamp.from(failedAt);
        Timestamp lockUntilTimestamp = Timestamp.from(lockUntil);
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO login_lock_states
                          (user_id, failed_login_count, locked_until, last_failed_at, updated_at)
                        VALUES (?, 1, CASE WHEN ? <= 1 THEN ? ELSE NULL END, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET failed_login_count = login_lock_states.failed_login_count + 1,
                            locked_until = CASE
                                WHEN login_lock_states.failed_login_count + 1 >= ? THEN ?
                                ELSE login_lock_states.locked_until
                            END,
                            last_failed_at = ?,
                            updated_at = ?
                        RETURNING failed_login_count, locked_until
                        """,
                // 将结果集映射为 LoginFailureState
                (rs, rowNum) -> new LoginFailureState(
                        rs.getInt("failed_login_count"),
                        // Timestamp 转 Instant，null 安全
                        toInstant(rs.getTimestamp("locked_until"))),
                // 以下为 SQL 占位符参数（按出现顺序）
                userId,                                          // $1:  INSERT user_id
                maxFailedAttempts,                               // $2:  CASE 判定阈值
                lockUntilTimestamp,                              // $3:  INSERT 分支锁定截止时间
                failedAtTimestamp,                               // $4:  INSERT last_failed_at
                failedAtTimestamp,                               // $5:  INSERT updated_at
                maxFailedAttempts,                               // $6:  UPDATE CASE 阈值
                lockUntilTimestamp,                              // $7:  UPDATE 分支锁定截止时间
                failedAtTimestamp,                               // $8:  UPDATE last_failed_at
                failedAtTimestamp);                              // $9:  UPDATE updated_at
    }

    /**
     * 记录一次登录成功。
     *
     * <p>使用 UPSERT 语法原子性地完成：
     * <ul>
     *   <li><strong>INSERT 分支（首次登录记录）：</strong>失败计数 0，
     *       锁定截止时间 NULL，写入最后登录时间；</li>
     *   <li><strong>UPDATE 分支（后续成功登录）：</strong>重置失败计数为 0，
     *       清除锁定截止时间（设为 NULL），更新最后登录时间。</li>
     * </ul>
     *
     * @param userId  用户 ID
     * @param loginAt 登录成功时间戳
     */
    @Override
    public void recordSuccessfulLogin(String userId, Instant loginAt) {
        Timestamp loginAtTimestamp = Timestamp.from(loginAt);
        jdbcTemplate.update(
                """
                        INSERT INTO login_lock_states
                          (user_id, failed_login_count, locked_until, last_failed_at, last_login_at, updated_at)
                        VALUES (?, 0, NULL, NULL, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        SET failed_login_count = 0,
                            locked_until = NULL,
                            last_login_at = ?,
                            updated_at = ?
                        """,
                userId,                   // INSERT user_id
                loginAtTimestamp,         // INSERT last_login_at
                loginAtTimestamp,         // INSERT updated_at
                loginAtTimestamp,         // UPDATE last_login_at
                loginAtTimestamp);        // UPDATE updated_at
    }

    /**
     * 将 JDBC 结果集的一行映射为 {@link LoginAccount} 领域对象。
     *
     * <p>字段映射规则：
     * <ul>
     *   <li>数据库列名使用下划线命名（snake_case），Java 字段使用驼峰命名（camelCase）；</li>
     *   <li>{@code locked_until} 为 {@link Timestamp} 类型，
     *       需通过 {@link #toInstant} 安全转换为 {@link Instant}（处理 null）。</li>
     * </ul>
     *
     * @param rs JDBC 结果集，定位在当前行
     * @return 映射后的领域对象
     * @throws SQLException 列访问异常
     */
    private static LoginAccount mapLoginAccount(ResultSet rs) throws SQLException {
        return new LoginAccount(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("user_status"),
                rs.getString("password_hash"),
                rs.getString("workspace_id"),
                toWorkspaceRole(rs.getString("workspace_role")),
                rs.getString("membership_status"),
                rs.getInt("failed_login_count"),
                // Timestamp → Instant 安全转换，null 安全
                toInstant(rs.getTimestamp("locked_until")));
    }

    /**
     * 将 JDBC {@link Timestamp} 安全转换为 {@link Instant}。
     *
     * <p>数据库中的时间字段可能为 {@code NULL}（如未锁定时 locked_until 为空），
     * 需做 null 安全检查，避免 {@link NullPointerException}。
     *
     * @param timestamp JDBC 时间戳，可能为 {@code null}
     * @return 对应的 Instant，若参数为 {@code null} 则返回 {@code null}
     */
    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * 将数据库中的工作区角色字符串转换为枚举。
     *
     * @param value 数据库角色值，可能为 null
     * @return 对应工作区角色；无成员关系时返回 null
     */
    private static WorkspaceRole toWorkspaceRole(String value) {
        return value == null ? null : WorkspaceRole.valueOf(value);
    }
}
