package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;

/**
 * 创建本地账号命令。
 *
 * <p>封装创建托管账号所需的全部参数，在紧凑构造函数中完成参数校验，
 * 并通过规范化方法对外提供安全的参数访问。
 *
 * @param username      用户名
 * @param displayName   展示名称
 * @param password      明文密码（由应用服务负责编码）
 * @param workspaceRole 工作区角色，需为 {@link WorkspaceRole} 枚举值
 */
public record CreateManagedAccountCommand(
        String username,
        String displayName,
        String password,
        String workspaceRole) {

    /**
     * 紧凑构造函数，对所有字段做非空校验。
     */
    public CreateManagedAccountCommand {
        username = requireText(username, "username is required");
        displayName = requireText(displayName, "displayName is required");
        password = requireText(password, "password is required");
        workspaceRole = requireText(workspaceRole, "workspaceRole is required");
    }

    /**
     * 返回去除前后空白后的用户名。
     *
     * @return 规范化用户名
     */
    public String normalizedUsername() {
        return username.trim();
    }

    /**
     * 返回去除前后空白后的展示名称。
     *
     * @return 规范化展示名称
     */
    public String normalizedDisplayName() {
        return displayName.trim();
    }

    /**
     * 返回去除前后空白后的明文密码。
     *
     * @return 规范化密码
     */
    public String normalizedPassword() {
        return password.trim();
    }

    /**
     * 将工作区角色字符串解析为 {@link WorkspaceRole} 枚举。
     *
     * @return 解析后的工作区角色
     * @throws IllegalArgumentException 如果角色字符串不是合法的枚举值
     */
    public WorkspaceRole resolvedWorkspaceRole() {
        try {
            // 去除前后空白后尝试匹配枚举值
            return WorkspaceRole.valueOf(workspaceRole.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid workspaceRole: " + workspaceRole, ex);
        }
    }

    /**
     * 校验字符串不能为空（null 或纯空白）。
     *
     * @param value   待校验字符串
     * @param message 校验失败时的异常消息
     * @return 原始字符串（校验通过时原样返回）
     * @throws IllegalArgumentException 如果 value 为 null 或纯空白
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
