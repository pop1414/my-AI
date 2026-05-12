package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 创建托管账号请求体。
 *
 * @param username      用户名
 * @param displayName   展示名称
 * @param password      初始密码
 * @param workspaceRole 工作区角色，如 WORKSPACE_MEMBER
 */
public record CreateManagedAccountRequest(
        String username,
        String displayName,
        String password,
        String workspaceRole) {
}
