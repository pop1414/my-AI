package io.github.spike.myai.auth.domain.model;

/**
 * 工作区成员领域模型（只读视图）。
 * <p>
 * 聚合用户基础信息与工作区成员关系的数据载体，用于在领域层与持久层之间传递。
 * 不承载业务行为，仅作为查询与更新编排的数据快照。
 * <p>
 * 设计为不可变 Record，由仓储实现通过 JDBC RowMapper 从数据库构造，
 * 确保一次构造后字段不可变，避免跨层传递中的意外篡改。
 *
 * @param userId           用户唯一标识
 * @param username         用户名（登录名）
 * @param displayName      展示名称（用于 UI 呈现）
 * @param workspaceId      所属工作区唯一标识
 * @param workspaceRole    工作区角色枚举（如 ADMIN / MEMBER）
 * @param membershipStatus 成员关系状态（如 ACTIVE / INACTIVE）
 * @author spike
 * @since 1.0.0
 */
public record WorkspaceMember(
        String userId,
        String username,
        String displayName,
        String workspaceId,
        WorkspaceRole workspaceRole,
        String membershipStatus) {
}
