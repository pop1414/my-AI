package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;

/**
 * 调整工作区成员角色命令。
 * <p>
 * 封装角色更新操作所需的全部入参，在构造阶段完成基础校验（非空、非空白），
 * 并提供规范化方法供下游用例层使用，避免重复的参数清理和校验逻辑。
 * <p>
 * 设计为不可变 Record，天然线程安全，适合在分层架构中跨层传递。
 *
 * @param userId        目标成员用户 ID，不能为空或纯空白
 * @param workspaceRole 目标工作区角色字符串，不能为空或纯空白，需可解析为 {@link WorkspaceRole} 枚举
 * @author spike
 * @since 1.0.0
 */
public record UpdateWorkspaceMemberRoleCommand(
        String userId,
        String workspaceRole) {

    /**
     * 紧凑构造器：在 Record 构造阶段执行参数校验。
     * <p>
     * Record 的紧凑构造器会在字段赋值前被调用，
     * 此处对两个必填字段进行非空及非空白校验，校验通过后才允许赋值。
     */
    public UpdateWorkspaceMemberRoleCommand {
        // 校验并规范化 userId，空白字符串将被拒绝
        userId = requireText(userId, "userId is required");
        // 校验并规范化 workspaceRole，空白字符串将被拒绝
        workspaceRole = requireText(workspaceRole, "workspaceRole is required");
    }

    /**
     * 获取去除首尾空白后的用户 ID。
     * <p>
     * 用于仓储层查询时的精确匹配，避免因前端传入多余空格导致查询失败。
     *
     * @return 规范化（trim）后的用户 ID
     */
    public String normalizedUserId() {
        return userId.trim();
    }

    /**
     * 将角色字符串解析为 {@link WorkspaceRole} 枚举值。
     * <p>
     * 解析前先进行 trim 处理，若字符串无法匹配任何枚举常量，
     * 则抛出携带原始输入值的 {@link IllegalArgumentException}，便于调用方定位问题。
     *
     * @return 对应的 {@link WorkspaceRole} 枚举值
     * @throws IllegalArgumentException 当角色字符串无法解析为有效枚举值时抛出
     */
    public WorkspaceRole resolvedWorkspaceRole() {
        try {
            // trim 后尝试匹配枚举常量
            return WorkspaceRole.valueOf(workspaceRole.trim());
        } catch (IllegalArgumentException ex) {
            // 保留原始输入值以便排查，包装后重新抛出
            throw new IllegalArgumentException("invalid workspaceRole: " + workspaceRole, ex);
        }
    }

    /**
     * 校验字符串是否为有效文本（非 {@code null} 且非纯空白）。
     * <p>
     * 作为紧凑构造器和静态工厂方法的公共校验入口，统一错误消息格式。
     *
     * @param value   待校验的字符串值
     * @param message 校验失败时的错误消息
     * @return 原始字符串（校验通过时原样返回，供紧凑构造器重新赋值使用）
     * @throws IllegalArgumentException 当值为 {@code null} 或纯空白时抛出
     */
    private static String requireText(String value, String message) {
        // null 或 trim 后为空均视为非法输入
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
