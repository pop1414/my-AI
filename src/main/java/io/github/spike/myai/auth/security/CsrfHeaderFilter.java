package io.github.spike.myai.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.shared.rest.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 自定义 CSRF 请求头校验过滤器。
 *
 * <p>本过滤器提供一种轻量级的 CSRF 防护方案，替代 Spring Security 默认的
 * Cookie-Token 同步器模式。其核心逻辑为：对于 {@code /api/v1/} 路径下
 * 的非安全 HTTP 方法（PUT / POST / DELETE / PATCH 等），要求客户端在
 * 请求头中携带特定的 CSRF 标识（{@link SecurityConstants#CSRF_HEADER_NAME}），
 * 且值必须与预期值（{@link SecurityConstants#CSRF_HEADER_VALUE}）匹配。
 *
 * <p>设计考量：
 * <ul>
 *   <li>安全方法（GET / HEAD / OPTIONS）直接放行，不产生副作用；</li>
 *   <li>仅拦截 {@code /api/v1/} 前缀的 API 请求，不影响其他路径；</li>
 *   <li>校验失败时返回 JSON 格式的 403 错误，保持前后端分离风格统一；</li>
 *   <li>继承 {@link OncePerRequestFilter}，保证单个请求只过滤一次。</li>
 * </ul>
 *
 * <p>安全升级路径：当前采用固定值校验（"存在且为 1"即通过），
 * 未来可升级为动态 Token（如每次登录后下发随机 CSRF Token）。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class CsrfHeaderFilter extends OncePerRequestFilter {

    /**
     * 安全 HTTP 方法集合。
     *
     * <p>GET / HEAD / OPTIONS 属于幂等且无副作用的读操作，
     * 不触发 CSRF 校验，直接放行。
     */
    private static final Set<String> SAFE_METHODS = Set.of(
            HttpMethod.GET.name(),
            HttpMethod.HEAD.name(),
            HttpMethod.OPTIONS.name());

    /** JSON 序列化工具，用于输出错误响应 */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     *
     * @param objectMapper Jackson ObjectMapper 实例
     */
    public CsrfHeaderFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 CSRF 请求头校验的核心过滤逻辑。
     *
     * <p>处理流程：
     * <ol>
     *   <li>判断当前请求是否需要 CSRF 校验（非安全方法 + /api/v1/ 路径）；</li>
     *   <li>若不需要，直接放行至下一个过滤器；</li>
     *   <li>从请求头中提取 CSRF 标识；</li>
     *   <li>与预期值比对：匹配则放行，不匹配则返回 403 JSON 错误。</li>
     * </ol>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException 过滤器链处理异常
     * @throws IOException      IO 异常（如写入响应体失败）
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 步骤1：判断当前请求是否需要 CSRF 头校验
        if (!requiresHeader(request)) {
            // 安全方法或非 API 路径，直接放行
            filterChain.doFilter(request, response);
            return;
        }

        // 步骤2：从请求头中获取自定义 CSRF 标识的值
        String value = request.getHeader(SecurityConstants.CSRF_HEADER_NAME);
        // 步骤3：与预期值比对，不匹配则拦截
        if (!SecurityConstants.CSRF_HEADER_VALUE.equals(value)) {
            // 设置 HTTP 403 状态码
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            // 设置响应内容类型为 JSON
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // 输出统一的 JSON 错误响应
            objectMapper.writeValue(
                    response.getWriter(),
                    new ErrorResponse("CSRF_HEADER_REQUIRED", "valid csrf header is required"));
            // 终止过滤链，不再向后传递
            return;
        }

        // 步骤4：校验通过，放行至下一个过滤器
        filterChain.doFilter(request, response);
    }

    /**
     * 判断当前请求是否需要 CSRF 请求头校验。
     *
     * <p>两个条件同时满足才需要校验：
     * <ol>
     *   <li>HTTP 方法不属于安全方法集合（即 GET / HEAD / OPTIONS 之外的方法）；</li>
     *   <li>请求 URI 以 {@code /api/v1/} 开头。</li>
     * </ol>
     *
     * @param request HTTP 请求
     * @return {@code true} 需要校验，{@code false} 直接放行
     */
    private static boolean requiresHeader(HttpServletRequest request) {
        // 非安全方法（PUT/POST/DELETE/PATCH 等）且属于 API v1 前缀路径
        return !SAFE_METHODS.contains(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/");
    }
}
