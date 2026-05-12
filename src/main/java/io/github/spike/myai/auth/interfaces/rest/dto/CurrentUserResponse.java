package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 当前用户信息响应 DTO。
 *
 * <p>用于多个接口的响应体中携带登录用户的身份信息，包括：
 * <ul>
 *   <li>登录接口（{@link LoginResponse}）；</li>
 *   <li>获取当前用户接口（{@code GET /api/v1/auth/me}）。</li>
 * </ul>
 * 使用 Java {@code record} 类型保证不可变性，所有字段均为必填。
 *
 * @param userId        用户唯一标识
 * @param username      用户名
 * @param displayName   展示名称（如昵称）
 * @param workspaceId    所属工作空间 ID
 * @param workspaceRole  工作空间角色
 * @param capabilities   当前用户能力位
 * @author spike
 * @since 1.0.0
 */
public record CurrentUserResponse(
        /** 用户唯一标识 */
        String userId,
        /** 用户名 */
        String username,
        /** 展示名称，用于前端界面显示 */
        String displayName,
        /** 所属工作空间 ID */
        String workspaceId,
        /** 工作空间角色，如 ADMIN / MEMBER 等 */
        String workspaceRole,
        /** 当前用户能力位 */
        CurrentUserCapabilitiesResponse capabilities) {
}
