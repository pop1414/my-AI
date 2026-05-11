package io.github.spike.myai.auth.application.command;

/**
 * 移除成员关系命令。
 *
 * <p>封装移除工作区成员关系所需的用户标识，在构造时完成参数校验。
 *
 * @param userId 目标用户唯一标识
 */
public record RemoveManagedAccountMembershipCommand(String userId) {

    /**
     * 紧凑构造函数，对 userId 做非空校验。
     */
    public RemoveManagedAccountMembershipCommand {
        userId = requireText(userId, "userId is required");
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
