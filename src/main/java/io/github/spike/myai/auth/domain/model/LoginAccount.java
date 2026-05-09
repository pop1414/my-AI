package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 登录账户领域模型。
 *
 * <p>聚合了登录所需的所有用户信息，由基础设施层的仓储
 * 通过多表联查（users + local_credentials + workspace_memberships +
 * login_lock_states）一次性加载，避免 N+1 查询问题。
 *
 * <p>此对象为只读视图，不包含业务行为方法（如密码校验、锁定判定等），
 * 此类逻辑由应用服务层（{@code LoginApplicationService}）负责编排。
 *
 * <p>使用 Java {@code record} 保证不可变性。
 *
 * @param userId           用户唯一标识（业务主键）
 * @param username         用户名
 * @param displayName      展示名称
 * @param userStatus       用户状态（ACTIVE / DISABLED）
 * @param passwordHash     BCrypt 密码哈希值
 * @param workspaceId      所属工作空间 ID
 * @param workspaceRole    工作空间角色
 * @param membershipStatus 成员资格状态（ACTIVE / INACTIVE）
 * @param failedLoginCount 连续登录失败次数
 * @param lockedUntil      锁定截止时间（{@code null} 表示未锁定）
 * @author spike
 * @since 1.0.0
 */
public record LoginAccount(
        /** 用户唯一标识（业务主键） */
        String userId,
        /** 用户名 */
        String username,
        /** 展示名称 */
        String displayName,
        /** 用户状态：ACTIVE 或 DISABLED */
        String userStatus,
        /** BCrypt 密码哈希值 */
        String passwordHash,
        /** 所属工作空间 ID */
        String workspaceId,
        /** 工作空间角色 */
        WorkspaceRole workspaceRole,
        /** 成员资格状态：ACTIVE 或 INACTIVE */
        String membershipStatus,
        /** 连续登录失败次数 */
        int failedLoginCount,
        /** 锁定截止时间，null 表示未锁定 */
        Instant lockedUntil) {
}
