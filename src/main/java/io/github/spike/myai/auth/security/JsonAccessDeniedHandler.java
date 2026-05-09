package io.github.spike.myai.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.shared.rest.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * JSON 格式的访问拒绝处理器。
 *
 * <p>当已认证用户尝试访问其无权限的资源时（即认证通过但授权失败），
 * Spring Security 会调用本处理器的 {@link #handle} 方法。
 *
 * <p>与 Spring Security 默认行为（返回 403 错误页或空白页面）不同，
 * 本实现返回 JSON 格式的 403 错误响应，适配前后端分离架构：
 * <pre>{@code
 * HTTP/1.1 403 Forbidden
 * Content-Type: application/json
 *
 * {"code": "FORBIDDEN", "message": "access is denied"}
 * }</pre>
 *
 * <p>典型触发场景：普通用户尝试访问管理员专属接口。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    /** JSON 序列化工具，用于输出错误响应 */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     *
     * @param objectMapper Jackson ObjectMapper 实例
     */
    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 处理授权拒绝请求。
     *
     * <p>当已认证用户缺乏所需权限时触发，
     * 返回 HTTP 403 状态码和 JSON 格式的统一错误信息。
     *
     * @param request               HTTP 请求
     * @param response              HTTP 响应
     * @param accessDeniedException 权限拒绝异常，包含拒绝原因
     * @throws IOException      响应写入失败时抛出
     * @throws ServletException Servlet 异常
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // 设置 HTTP 403 状态码（Forbidden）
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // 设置响应内容类型为 JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 输出统一的 JSON 错误响应体
        objectMapper.writeValue(response.getWriter(), new ErrorResponse("FORBIDDEN", "access is denied"));
    }
}
