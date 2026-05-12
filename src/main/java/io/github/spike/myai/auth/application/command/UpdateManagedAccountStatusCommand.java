package io.github.spike.myai.auth.application.command;

/**
 * 更新账号状态命令。
 *
 * <p>封装账号状态变更所需的用户标识和目标状态，在构造时校验参数合法性，
 * 并通过 {@link #resolvedUserStatus()} 对状态值做白名单校验。
 *
 * @param userId     目标用户唯一标识
 * @param userStatus 目标状态，仅允许 {@code ACTIVE} 或 {@code DISABLED}
 */
public record UpdateManagedAccountStatusCommand(
        String userId,
        String userStatus) {

    /**
     * 紧凑构造函数，对所有字段做非空校验。
     */
    public UpdateManagedAccountStatusCommand {
        userId = requireText(userId, "userId is required");
        userStatus = requireText(userStatus, "userStatus is required");
    }

    /**
     * 返回去除前后空白后的用户 ID。
     *
     * @return 规范化用户 ID
     */
    public String normalizedUserId() {
        return userId.trim();
    }

    /**
     * 对用户状态做白名单校验并返回规范化值。
     *
     * <p>仅允许 {@code ACTIVE} 和 {@code DISABLED} 两种状态，
     * 避免传入非法状态值污染数据。
     *
     * @return 规范化状态值
     * @throws IllegalArgumentException 如果状态不在白名单中
     */
    public String resolvedUserStatus() {
        // 去除前后空白后进行白名单校验
        String normalized = userStatus.trim();
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("invalid userStatus: " + userStatus);
        }
        return normalized;
    }

    /**
     * 校验字符串不能为空（null 或纯空白）。
     *
     * @param value   待校验字符串
     * @param message 校验失败时的异常消息
     * @return 原始字符串
     * @throws IllegalArgumentException 如果 value 为 null 或纯空白
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
