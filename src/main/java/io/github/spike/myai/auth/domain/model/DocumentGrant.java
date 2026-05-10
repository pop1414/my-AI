package io.github.spike.myai.auth.domain.model;

/**
 * 文档级授权领域模型（只读视图）。
 * <p>
 * 聚合文档级权限覆盖与被授权成员基础信息的数据载体，用于在领域层与持久层之间传递。
 * 不承载业务行为，仅作为查询与更新编排的数据快照。
 * <p>
 * 设计为不可变 Record，由仓储实现通过 JDBC RowMapper 从
 * {@code document_grants} 表与 {@code users} 表联表查询构造。
 *
 * @param workspaceId 工作区唯一标识
 * @param documentId  文档唯一标识
 * @param userId      被授权用户唯一标识
 * @param username    被授权用户名（登录名）
 * @param displayName 被授权用户展示名称（用于 UI 呈现）
 * @param permission  文档权限覆盖枚举（如 READ / WRITE）
 * @param status      授权状态（ACTIVE / DISABLED）
 * @author spike
 * @since 1.0.0
 */
public record DocumentGrant(
        String workspaceId,
        String documentId,
        String userId,
        String username,
        String displayName,
        DocumentPermission permission,
        String status) {
}
