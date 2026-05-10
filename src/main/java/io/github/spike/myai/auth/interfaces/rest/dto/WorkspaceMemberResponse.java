package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 工作区成员响应 DTO。
 * <p>
 * 用于工作区成员列表查询及角色更新接口的 JSON 响应体。
 * 由控制器中的 {@code toResponse} 方法从
 * {@link io.github.spike.myai.auth.application.result.WorkspaceMemberResult} 转换而来。
 * <p>
 * 注意：{@code workspaceRole} 在此处为 {@link String} 类型（枚举的 {@code name()}），
 * 而非领域层使用的 {@link io.github.spike.myai.auth.domain.model.WorkspaceRole} 枚举，
 * 以确保 REST 接口返回的 JSON 中为可读字符串。
 *
 * @param userId           用户唯一标识
 * @param username         用户名（登录名）
 * @param displayName      展示名称（用于 UI 呈现）
 * @param workspaceId      所属工作区 ID
 * @param workspaceRole    工作区角色字符串（枚举 name，如 "ADMIN" / "MEMBER"）
 * @param membershipStatus 成员关系状态（如 "ACTIVE" / "INACTIVE"）
 * @author spike
 * @since 1.0.0
 */
public record WorkspaceMemberResponse(
        String userId,
        String username,
        String displayName,
        String workspaceId,
        String workspaceRole,
        String membershipStatus) {
}
