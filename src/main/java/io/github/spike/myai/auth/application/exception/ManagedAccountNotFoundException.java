package io.github.spike.myai.auth.application.exception;

/**
 * 账号不存在异常。
 *
 * <p>当按工作区和用户 ID 查询托管账号时，若目标账号不存在则抛出此异常，
 * 由 Controller 层统一转换为 HTTP 404 响应。
 */
public class ManagedAccountNotFoundException extends RuntimeException {

    /**
     * 构造指定消息的异常。
     *
     * @param message 异常描述信息
     */
    public ManagedAccountNotFoundException(String message) {
        super(message);
    }
}
