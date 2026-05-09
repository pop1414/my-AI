package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.BootstrapAdminAccount;
import io.github.spike.myai.auth.domain.port.BootstrapAdminRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 初始管理员账号引导的 JDBC 仓储实现。
 *
 * <p>实现 {@link BootstrapAdminRepository} 端口，使用 {@link JdbcTemplate}
 * 对 PostgreSQL 执行三表 UPSERT 操作，完成空库引导。
 *
 * <p>幂等性设计：所有写操作使用 {@code INSERT ... ON CONFLICT ... DO UPDATE}
 * (UPSERT) 语义，确保：
 * <ul>
 *   <li>服务重启时重复执行不产生重复记录；</li>
 *   <li>多实例并发启动时不会因主键冲突失败；</li>
 *   <li>密码/角色等字段可被更新（如通过环境变量修改密码后重启）。</li>
 * </ul>
 *
 * <p>涉及的唯一约束：
 * <ul>
 *   <li>{@code users.username} —— 按用户名冲突时更新，用于幂等；</li>
 *   <li>{@code local_credentials.user_id} —— 按用户 ID 冲突时更新密码；</li>
 *   <li>{@code workspace_memberships(workspace_id, user_id)} ——
 *       按复合键冲突时更新角色。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcBootstrapAdminRepository implements BootstrapAdminRepository {

    /** Spring JDBC 模板，用于执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcBootstrapAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 统计指定工作空间下的成员数量。
     *
     * <p>执行简单的 {@code SELECT COUNT(*)} 查询，
     * 对 null 做防御处理（理论上 COUNT 不会返回 null）。
     *
     * @param workspaceId 工作空间 ID
     * @return 成员数量，查询异常时返回 0
     */
    @Override
    public int countWorkspaceMemberships(String workspaceId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM workspace_memberships
                        WHERE workspace_id = ?
                        """,
                Integer.class,
                workspaceId);
        // 防御性处理：理论上 COUNT 不会为 null，但做 null 安全兜底
        return count == null ? 0 : count;
    }

    /**
     * 执行三表 UPSERT，写入完整的初始管理员账号。
     *
     * <p>写入顺序遵循外键依赖：users → local_credentials / workspace_memberships。
     * 三个操作在应用层事务的保证下原子执行。
     *
     * @param account 初始管理员账号写入模型
     * @return 写入的用户 ID（首次 INSERT 返回新 ID，幂等调用返回已有 ID）
     */
    @Override
    public String saveBootstrapAdmin(BootstrapAdminAccount account) {
        // 步骤1：写入或更新 users 表，返回实际用户 ID
        String userId = upsertUser(account);
        // 步骤2：写入或更新 local_credentials 表（密码凭证）
        upsertLocalCredential(account, userId);
        // 步骤3：写入或更新 workspace_memberships 表（工作空间成员关系）
        upsertWorkspaceMembership(account, userId);
        return userId;
    }

    /**
     * UPSERT 用户记录到 {@code users} 表。
     *
     * <p>冲突策略（按 {@code username} 唯一约束）：
     * <ul>
     *   <li><strong>INSERT 分支：</strong>使用给定的 user_id 和 ACTIVE 状态创建新用户；</li>
     *   <li><strong>UPDATE 分支：</strong>若已有用户且 display_name 为空，则用新值补齐；
     *       否则保留原有 display_name（不覆盖用户手动修改的展示名称）；
     *       确保状态为 ACTIVE，更新 updated_at 时间戳。</li>
     * </ul>
     *
     * <p>通过 {@code RETURNING user_id} 获取实际用户 ID（首次为新 UUID，
     * 幂等调用为已有 ID），供后续 credentials 和 memberships 写入使用。
     *
     * @param account 引导管理员账号
     * @return 实际写入或复用的用户 ID
     */
    private String upsertUser(BootstrapAdminAccount account) {
        Timestamp now = Timestamp.from(account.createdAt());
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO users
                          (user_id, username, display_name, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                        ON CONFLICT (username) DO UPDATE
                        SET display_name = CASE
                                -- 仅当原 display_name 为空时采用新值，保留用户手动修改的名称
                                WHEN users.display_name = '' THEN EXCLUDED.display_name
                                ELSE users.display_name
                            END,
                            -- 确保账户状态为 ACTIVE（恢复可能被禁用的账号）
                            status = 'ACTIVE',
                            updated_at = EXCLUDED.updated_at
                        RETURNING user_id
                        """,
                String.class,
                account.userId(),
                account.username(),
                account.displayName(),
                now,
                now);
    }

    /**
     * UPSERT 密码凭证到 {@code local_credentials} 表。
     *
     * <p>冲突策略（按 {@code user_id} 唯一约束）：
     * <ul>
     *   <li><strong>INSERT 分支：</strong>创建新的密码凭证记录；</li>
     *   <li><strong>UPDATE 分支：</strong>用最新密码哈希覆盖旧值，
     *       同时更新密码算法标识和密码更新时间戳。
     *       这允许通过修改环境变量后重启来重置管理员密码。</li>
     * </ul>
     *
     * @param account 引导管理员账号
     * @param userId  用户 ID（来自 upsertUser 的返回值）
     */
    private void upsertLocalCredential(BootstrapAdminAccount account, String userId) {
        Timestamp now = Timestamp.from(account.createdAt());
        jdbcTemplate.update(
                """
                        INSERT INTO local_credentials
                          (user_id, password_hash, password_algo, password_updated_at, created_at, updated_at)
                        VALUES (?, ?, 'bcrypt', ?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                        -- 冲突时用新值覆盖密码哈希和相关字段，支持密码重置
                        SET password_hash = EXCLUDED.password_hash,
                            password_algo = 'bcrypt',
                            password_updated_at = EXCLUDED.password_updated_at,
                            updated_at = EXCLUDED.updated_at
                        """,
                userId,
                account.passwordHash(),
                now,
                now,
                now);
    }

    /**
     * UPSERT 工作空间成员关系到 {@code workspace_memberships} 表。
     *
     * <p>冲突策略（按 {@code (workspace_id, user_id)} 复合唯一约束）：
     * <ul>
     *   <li><strong>INSERT 分支：</strong>创建新的 ACTIVE 成员关系；</li>
     *   <li><strong>UPDATE 分支：</strong>更新角色（允许通过配置变更角色）、
     *       确保状态为 ACTIVE（恢复可能被移除的成员）。</li>
     * </ul>
     *
     * @param account 引导管理员账号
     * @param userId  用户 ID（来自 upsertUser 的返回值）
     */
    private void upsertWorkspaceMembership(BootstrapAdminAccount account, String userId) {
        Timestamp now = Timestamp.from(account.createdAt());
        jdbcTemplate.update(
                """
                        INSERT INTO workspace_memberships
                          (workspace_id, user_id, role, status, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                        ON CONFLICT (workspace_id, user_id) DO UPDATE
                        -- 冲突时更新角色和状态，支持角色变更和成员恢复
                        SET role = EXCLUDED.role,
                            status = 'ACTIVE',
                            updated_at = EXCLUDED.updated_at
                        """,
                account.workspaceId(),
                userId,
                account.role(),
                now,
                now);
    }
}
