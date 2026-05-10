package io.github.spike.myai.auth.domain.model;

/**
 * 知识库授权领域模型（只读视图）。
 * <p>
 * 聚合授权关系与被授权成员基础信息的数据载体，用于在领域层与持久层之间传递。
 * 不承载业务行为，仅作为查询与更新编排的数据快照。
 * <p>
 * 设计为不可变 Record，由仓储实现通过 JDBC RowMapper 从
 * {@code knowledge_base_grants} 表与 {@code users} 表联表查询构造。
 *
 * @param workspaceId 工作区唯一标识
 * @param kbId        知识库唯一标识
 * @param userId      被授权用户唯一标识
 * @param username    被授权用户名（登录名）
 * @param displayName 被授权用户展示名称（用于 UI 呈现）
 * @param role        知识库授权角色枚举（如 VIEWER / EDITOR）
 * @param status      授权状态（ACTIVE / DISABLED）
 * @author spike
 * @since 1.0.0
 */
public record KnowledgeBaseGrant(
        String workspaceId,
        String kbId,
        String userId,
        String username,
        String displayName,
        KnowledgeBaseRole role,
        String status) {
}
