package io.github.spike.myai.auth.application.exception;

/**
 * 用户名冲突异常。
 *
 * <p>创建托管账号时若用户名已被占用则抛出此异常，
 * 由 Controller 层统一转换为 HTTP 409 响应。
 */
public class ManagedAccountUsernameConflictException extends RuntimeException {

    /**
     * 构造指定消息的异常。
     *
     * @param message 异常描述信息
     */
    public ManagedAccountUsernameConflictException(String message) {
        super(message);
    }
}
