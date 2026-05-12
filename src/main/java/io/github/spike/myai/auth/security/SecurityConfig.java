package io.github.spike.myai.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Spring Security 核心安全配置类。
 *
 * <p>本类负责定义整个应用的安全策略，包括：
 * <ul>
 *   <li>HTTP 安全过滤链（SecurityFilterChain）的构建；</li>
 *   <li>会话管理与安全上下文存储策略；</li>
 *   <li>密码编码器（BCrypt）；</li>
 *   <li>用户详情服务（占位实现，后续对接真实用户数据源）；</li>
 *   <li>自定义认证入口点、访问拒绝处理器、CSRF 过滤器。</li>
 * </ul>
 *
 * <p>注意：本类禁用了 Spring Security 默认的表单登录、HTTP Basic 认证、
 * CSRF 及登出功能，采用自定义的 JSON 响应格式和轻量 CSRF 头校验机制，
 * 以适配前后端分离架构。
 *
 * @author spike
 * @since 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 构建核心安全过滤链。
     *
     * <p>职责：定义 HTTP 请求的安全拦截规则，包括：
     * <ol>
     *   <li>禁用默认的 CSRF、表单登录、HTTP Basic、登出机制；</li>
     *   <li>配置会话策略为按需创建（IF_REQUIRED），避免无状态 API 产生冗余会话；</li>
     *   <li>指定安全上下文持久化方式为 HttpSession；</li>
     *   <li>注入自定义的 JSON 格式认证入口点和访问拒绝处理器；</li>
     *   <li>声明白名单路径（登录接口、健康检查等）和认证要求；</li>
     *   <li>在 AuthorizationFilter 之前插入自定义 CSRF 头校验过滤器。</li>
     * </ol>
     *
     * @param http                    Spring Security 的 HttpSecurity 构建器
     * @param authenticationEntryPoint 自定义认证失败处理器（返回 JSON）
     * @param accessDeniedHandler      自定义授权失败处理器（返回 JSON）
     * @param csrfHeaderFilter         自定义 CSRF 请求头校验过滤器
     * @return 组装完成的 SecurityFilterChain 实例
     * @throws Exception 配置过程中可能抛出的异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            CsrfHeaderFilter csrfHeaderFilter) throws Exception {
        // 禁用 CSRF 保护（本应用使用自定义 CSRF 头校验，不依赖 Spring Security 默认的同步器模式）
        http.csrf(AbstractHttpConfigurer::disable)
                // 禁用默认表单登录（前后端分离架构下不使用服务端渲染的登录页）
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用 HTTP Basic 认证（改用自定义登录接口 + Session 方式）
                .httpBasic(AbstractHttpConfigurer::disable)
                // 禁用默认登出逻辑（由应用层自行处理登出流程）
                .logout(AbstractHttpConfigurer::disable)
                // 会话管理：仅在需要时创建 HttpSession，避免无状态端点产生不必要的会话开销
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // 指定 SecurityContext 的持久化方式为 HttpSession，确保认证状态在请求间保持
                .securityContext(context -> context.securityContextRepository(securityContextRepository()))
                // 异常处理：统一返回 JSON 格式的错误响应，而非默认的 302 重定向或 HTML 错误页
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 请求授权规则配置
                .authorizeHttpRequests(authorize -> authorize
                        // POST /api/v1/auth/login —— 登录接口，允许匿名访问
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // Actuator 健康检查和信息端点 —— 供监控系统使用，允许匿名访问
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // 其余所有请求必须经过认证
                        .anyRequest().authenticated())
                // 在授权过滤器之前插入自定义 CSRF 头校验过滤器，实现轻量级 CSRF 防护
                .addFilterBefore(csrfHeaderFilter, AuthorizationFilter.class);
        // 构建并返回最终的 SecurityFilterChain
        return http.build();
    }

    /**
     * 提供基于 HttpSession 的安全上下文存储实现。
     *
     * <p>SecurityContext 在请求处理完成后会被自动写入 HttpSession，
     * 下次请求时从 Session 中恢复，从而保持用户的认证状态。
     *
     * @return HttpSessionSecurityContextRepository 实例
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * 提供 BCrypt 密码编码器。
     *
     * <p>BCrypt 是一种基于 Blowfish 加密算法的自适应哈希函数，
     * 内置盐值且计算成本可调节（默认 strength=10），
     * 能有效抵御彩虹表攻击和暴力破解。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 提供用户详情服务的占位实现。
     *
     * <p>当前为临时实现，所有通过用户名加载用户的请求均抛出异常。
     * 后续需要对接真实用户数据源（如数据库、LDAP、OAuth2 等）时替换此 Bean。
     * 此处返回一个占位实现而非 {@code null}，是为了避免 Spring Security 自动配置
     * 生成默认的基于内存的用户密码，从而导致安全风险。
     *
     * @return 占位的 UserDetailsService 实现
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            // 占位实现：当前无真实用户数据源，任何用户名查询均视为未找到
            throw new UsernameNotFoundException(username);
        };
    }
}
