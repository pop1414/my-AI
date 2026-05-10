package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 知识库授权响应 DTO。
 * <p>
 * 用于知识库授权列表查询及 Upsert 接口的 JSON 响应体。
 * 由控制器中的 {@code toResponse} 方法从 {@link io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult} 转换而来。
 * <p>
 * 所有字段均为 {@link String} 类型，确保 JSON 序列化后的可读性。
 * {@code role} 字段存储枚举的 {@code name()} 值（如 "VIEWER" / "EDITOR"）。
 *
 * @param workspaceId 工作区唯一标识
 * @param kbId        知识库唯一标识
 * @param userId      被授权用户唯一标识
 * @param username    被授权用户名（登录名）
 * @param displayName 被授权用户展示名称（用于 UI 呈现）
 * @param role        知识库角色字符串（枚举 name，如 "VIEWER" / "EDITOR"）
 * @param status      授权状态（"ACTIVE" / "DISABLED"）
 * @author spike
 * @since 1.0.0
 */
public record KnowledgeBaseGrantResponse(
        String workspaceId,
        String kbId,
        String userId,
        String username,
        String displayName,
        String role,
        String status) {
}
