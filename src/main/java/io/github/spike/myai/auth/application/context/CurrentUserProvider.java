package io.github.spike.myai.auth.application.context;

import java.util.Optional;

/**
 * 当前用户上下文提供器（入站端口）。
 *
 * <p>定义获取当前登录用户的抽象契约，使业务应用服务在需要获取
 * 当前用户身份时，依赖此接口而非 Spring Security 的静态方法
 * （如 {@code SecurityContextHolder.getContext()}）。
 *
 * <p>设计收益：
 * <ul>
 *   <li><strong>框架解耦：</strong>应用层不直接依赖 Spring Security；</li>
 *   <li><strong>可测试性：</strong>测试中可注入模拟实现，无需构造安全上下文；</li>
 *   <li><strong>统一入口：</strong>所有用户身份获取逻辑集中管理，便于审计和日志。</li>
 * </ul>
 *
 * <p>当前唯一实现：
 * {@link io.github.spike.myai.auth.security.SpringSecurityCurrentUserProvider}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface CurrentUserProvider {

    /**
     * 获取当前登录用户（安全模式）。
     *
     * <p>适用于"可选认证"场景——当前用户可能登录也可能未登录，
     * 调用方自行判断 {@link Optional} 是否为空并决定后续行为。
     *
     * <p>返回 {@link Optional#empty()} 的典型场景：
     * <ul>
     *   <li>请求未携带任何认证凭证（匿名访问）；</li>
     *   <li>认证已过期但尚未触发认证入口点；</li>
     *   <li>认证主体类型不是 {@link io.github.spike.myai.auth.security.MyAiPrincipal}。</li>
     * </ul>
     *
     * @return 包装当前用户的 Optional；未登录或认证主体不匹配时返回 {@link Optional#empty()}
     */
    Optional<CurrentUser> currentUser();

    /**
     * 获取当前登录用户（强制模式）。
     *
     * <p>适用于"强制认证"场景——调用方要求当前用户必须已登录，
     * 否则抛出认证异常终止请求处理。
     *
     * <p>实现应委托 {@link #currentUser()} 获取 Optional，
     * 当其为空时抛出运行时异常（如
     * {@link org.springframework.security.authentication.AuthenticationCredentialsNotFoundException}）。
     *
     * @return 当前用户（保证非 {@code null}）
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException 当前用户未认证时抛出
     */
    CurrentUser requireCurrentUser();
}
