package io.github.spike.myai.auth.security;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Security 的当前用户上下文提供器（适配器实现）。
 *
 * <p>该实现位于六边形架构的安全适配层，作为
 * {@link CurrentUserProvider} 的应用层端口与 Spring Security
 * 基础设施之间的适配器（Anti-Corruption Layer）。
 *
 * <p>职责：
 * <ul>
 *   <li>从 {@link SecurityContextHolder} 的策略中获取当前线程的
 *       {@link SecurityContext}；</li>
 *   <li>提取 {@link Authentication#getPrincipal()} 并校验是否为
 *       {@link MyAiPrincipal} 类型；</li>
 *   <li>将 {@link MyAiPrincipal} 映射为应用层 {@link CurrentUser}，
 *       隔离 Spring Security 类型对应用层的污染。</li>
 * </ul>
 *
 * <p>使用 {@link SecurityContextHolderStrategy} 而非静态方法
 * {@code SecurityContextHolder.getContext()}，以便支持不同
 * 的上下文传播策略（如 {@code MODE_THREADLOCAL}、{@code MODE_INHERITABLETHREADLOCAL}、
 * {@code MODE_GLOBAL}），同时便于单元测试中注入模拟策略。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class SpringSecurityCurrentUserProvider implements CurrentUserProvider {

    /**
     * 安全上下文持有器策略。
     *
     * <p>通过 {@link SecurityContextHolder#getContextHolderStrategy()}
     * 获取当前配置的策略（默认 MODE_THREADLOCAL），而非硬编码静态调用，
     * 便于测试中替换为模拟策略。
     */
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * 获取当前登录用户（安全模式）。
     *
     * <p>处理流程：
     * <ol>
     *   <li>从当前线程的安全上下文中提取 {@link Authentication} 对象；</li>
     *   <li>若认证对象为 {@code null} 或未通过认证（如匿名令牌），返回空；</li>
     *   <li>若主体类型不是 {@link MyAiPrincipal}（如使用了其他认证提供器），
     *       返回空；</li>
     *   <li>从主体中提取业务字段，构建应用层 {@link CurrentUser} 并返回。</li>
     * </ol>
     *
     * @return 包装当前用户的 Optional；未登录或认证主体不匹配时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<CurrentUser> currentUser() {
        // 步骤1：从当前线程的安全上下文中获取认证对象
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        // 步骤2：认证对象为空或未通过认证（如匿名令牌），返回空
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        // 步骤3：主体类型不是 MyAiPrincipal（如 OAuth2 等其他认证方式），返回空
        if (!(authentication.getPrincipal() instanceof MyAiPrincipal principal)) {
            return Optional.empty();
        }
        // 步骤4：从 MyAiPrincipal 提取业务字段，构建应用层 CurrentUser
        return Optional.of(new CurrentUser(
                principal.userId(),
                principal.username(),
                principal.workspaceId(),
                principal.workspaceRole()));
    }

    /**
     * 获取当前登录用户（强制模式）。
     *
     * <p>委托 {@link #currentUser()} 获取 Optional，当为空时抛出
     * {@link AuthenticationCredentialsNotFoundException}，
     * 该异常由全局异常处理器映射为 HTTP 401 响应。
     *
     * @return 当前用户（保证非 {@code null}）
     * @throws AuthenticationCredentialsNotFoundException 当前用户未认证时抛出
     */
    @Override
    public CurrentUser requireCurrentUser() {
        // 委托安全模式获取，若为空则抛出认证异常
        return currentUser().orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("authentication is required"));
    }
}
