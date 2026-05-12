package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 登录响应 DTO。
 *
 * <p>定义 {@code POST /api/v1/auth/login} 接口的成功响应体结构，
 * 内嵌当前用户信息。使用 Java {@code record} 类型保证不可变性。
 *
 * @param user 当前登录用户信息
 * @author spike
 * @since 1.0.0
 */
public record LoginResponse(
        /** 当前登录用户信息 */
        CurrentUserResponse user) {
}
