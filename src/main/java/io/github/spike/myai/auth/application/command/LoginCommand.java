package io.github.spike.myai.auth.application.command;

/**
 * 登录命令对象。
 *
 * <p>封装登录用例的输入参数，由入站适配器（如 {@code AuthController}）
 * 创建并传递给 {@link io.github.spike.myai.auth.application.usecase.LoginUseCase}。
 * 使用 Java {@code record} 类型保证不可变性。
 *
 * @param username 用户名
 * @param password 明文密码（由应用层负责校验和比对）
 * @author spike
 * @since 1.0.0
 */
public record LoginCommand(
        /** 用户名 */
        String username,
        /** 明文密码 */
        String password) {
}
