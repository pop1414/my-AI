package io.github.spike.myai.auth.application.result;

import io.github.spike.myai.auth.domain.model.DocumentPermission;

/**
 * 文档授权结果对象。
 * <p>
 * 用例层返回的不可变数据传输对象，用于在应用服务与控制器之间传递文档级授权信息。
 * 聚合了授权关系（文档、用户、权限、状态）与被授权用户的基础信息。
 * 设计为 Record，字段只读，天然线程安全。
 *
 * @param workspaceId 工作区唯一标识
 * @param documentId  文档唯一标识
 * @param userId      被授权用户唯一标识
 * @param username    被授权用户名（登录名）
 * @param displayName 被授权用户展示名称（用于 UI 呈现）
 * @param permission  文档权限覆盖枚举（如 READ / WRITE）
 * @param status      授权状态（如 ACTIVE / DISABLED）
 * @author spike
 * @since 1.0.0
 */
public record DocumentGrantResult(
        String workspaceId,
        String documentId,
        String userId,
        String username,
        String displayName,
        DocumentPermission permission,
        String status) {
}
