package io.github.spike.myai.shared.rest;

/**
 * 通用错误响应 DTO。
 *
 * <p>定义全局统一的 API 错误响应体结构，供全局异常处理器（如
 * {@code GlobalExceptionHandler}）或手动抛出异常时使用。
 * 所有业务/系统错误均以此格式返回给客户端，确保错误信息的一致性。
 *
 * <p>使用 Java {@code record} 类型保证不可变性。
 *
 * @param code    错误码（如 {@code "AUTH_001"}、{@code "VALIDATION_ERROR"}）
 * @param message 面向用户的错误描述信息
 * @author spike
 * @since 1.0.0
 */
public record ErrorResponse(
        /** 错误码，用于客户端定位问题类型 */
        String code,
        /** 面向用户的错误描述信息 */
        String message) {
}
