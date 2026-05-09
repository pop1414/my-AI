package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 初始管理员账号写入模型。
 *
 * <p>仅服务于空库引导场景（"零号用户"创建），承载写入
 * {@code users}、{@code local_credentials} 与 {@code workspace_memberships}
 * 三张表所需的最小字段集合。
 *
 * <p>与 {@link LoginAccount} 的区别：
 * <ul>
 *   <li>本模型用于写入（INSERT / UPSERT），{@link LoginAccount} 用于查询；</li>
 *   <li>本模型不含运行时状态字段（如 userStatus、membershipStatus、
 *       failedLoginCount、lockedUntil），这些字段在写入时使用默认值；</li>
 *   <li>本模型使用 Java {@code record} 保证不可变性。</li>
 * </ul>
 *
 * @param userId       用户唯一标识（由应用层生成，通常为随机 UUID）
 * @param username     登录用户名（已去除首尾空白）
 * @param displayName  展示名称（已回退处理，保证非空）
 * @param passwordHash BCrypt 编码后的密码哈希
 * @param workspaceId  工作空间 ID
 * @param role         工作空间角色（固定为 {@link WorkspaceRole#WORKSPACE_OWNER}）
 * @param createdAt    创建时间戳（UTC）
 * @author spike
 * @since 1.0.0
 */
public record BootstrapAdminAccount(
        /** 用户唯一标识，由应用层通过 UUID 生成 */
        String userId,
        /** 登录用户名，已去除首尾空白 */
        String username,
        /** 展示名称，已回退处理保证非空 */
        String displayName,
        /** BCrypt 编码后的密码哈希值 */
        String passwordHash,
        /** 工作空间 ID */
        String workspaceId,
        /** 工作空间角色，固定为 WORKSPACE_OWNER */
        WorkspaceRole role,
        /** 创建时间戳（UTC） */
        Instant createdAt) {
}
