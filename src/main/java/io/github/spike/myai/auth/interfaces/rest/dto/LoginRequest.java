package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 登录请求 DTO。
 *
 * <p>定义 {@code POST /api/v1/auth/login} 接口的请求体结构。
 * 使用 Java {@code record} 类型，自动生成不可变字段、
 * 全参构造器、equals/hashCode/toString 及访问器方法。
 *
 * @param username 用户名
 * @param password 明文密码
 * @author spike
 * @since 1.0.0
 */
public record LoginRequest(
        /** 用户名 */
        String username,
        /** 明文密码 */
        String password) {
}
