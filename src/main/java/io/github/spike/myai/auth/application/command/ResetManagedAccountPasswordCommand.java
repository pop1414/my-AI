package io.github.spike.myai.auth.application.command;

/**
 * 重置账号密码命令。
 *
 * <p>封装密码重置所需的用户标识和新密码，构造时完成参数校验。
 *
 * @param userId   目标用户唯一标识
 * @param password 新密码明文（由应用服务负责编码）
 */
public record ResetManagedAccountPasswordCommand(
        String userId,
        String password) {

    /**
     * 紧凑构造函数，对所有字段做非空校验。
     */
    public ResetManagedAccountPasswordCommand {
        userId = requireText(userId, "userId is required");
        password = requireText(password, "password is required");
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
     * 返回去除前后空白后的新密码明文。
     *
     * @return 规范化密码
     */
    public String normalizedPassword() {
        return password.trim();
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
