package io.github.spike.myai.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.shared.rest.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * JSON 格式的认证入口点。
 *
 * <p>当用户访问需要认证的资源但未登录（即 Anonymous 或未携带有效凭证）时，
 * Spring Security 会调用本入口点的 {@link #commence} 方法。
 *
 * <p>与 Spring Security 默认行为（重定向到登录页或返回 HTTP Basic 质询头）不同，
 * 本实现返回 JSON 格式的 401 错误响应，适配前后端分离架构：
 * <pre>{@code
 * HTTP/1.1 401 Unauthorized
 * Content-Type: application/json
 *
 * {"code": "UNAUTHORIZED", "message": "authentication is required"}
 * }</pre>
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** JSON 序列化工具，用于输出错误响应 */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     *
     * @param objectMapper Jackson ObjectMapper 实例
     */
    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理未认证请求。
     *
     * <p>当用户未登录尝试访问受保护资源时触发，
     * 返回 HTTP 401 状态码和 JSON 格式的统一错误信息。
     *
     * @param request       HTTP 请求
     * @param response      HTTP 响应
     * @param authException 触发此入口点的认证异常（如未携带凭证）
     * @throws IOException      响应写入失败时抛出
     * @throws ServletException Servlet 异常
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        // 设置 HTTP 401 状态码（Unauthorized）
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 设置响应内容类型为 JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 输出统一的 JSON 错误响应体
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("UNAUTHORIZED", "authentication is required"));
    }
}
