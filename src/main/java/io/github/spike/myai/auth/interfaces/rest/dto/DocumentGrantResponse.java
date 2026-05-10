package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 文档授权响应 DTO。
 * <p>
 * 用于文档授权列表查询及 Upsert 接口的 JSON 响应体。
 * 由控制器中的 {@code toResponse} 方法从 {@link io.github.spike.myai.auth.application.result.DocumentGrantResult} 转换而来。
 * <p>
 * 所有字段均为 {@link String} 类型，确保 JSON 序列化后的可读性。
 * {@code permission} 字段存储枚举的 {@code name()} 值（如 "READ" / "WRITE"）。
 *
 * @param workspaceId 工作区唯一标识
 * @param documentId  文档唯一标识
 * @param userId      被授权用户唯一标识
 * @param username    被授权用户名（登录名）
 * @param displayName 被授权用户展示名称（用于 UI 呈现）
 * @param permission  文档权限字符串（枚举 name，如 "READ" / "WRITE"）
 * @param status      授权状态（"ACTIVE" / "DISABLED"）
 * @author spike
 * @since 1.0.0
 */
public record DocumentGrantResponse(
        String workspaceId,
        String documentId,
        String userId,
        String username,
        String displayName,
        String permission,
        String status) {
}
