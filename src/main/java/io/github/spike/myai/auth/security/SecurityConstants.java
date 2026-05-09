package io.github.spike.myai.auth.security;

/**
 * 安全模块常量定义类。
 *
 * <p>集中管理安全相关的常量值，避免硬编码分散在各处，便于统一维护与修改。
 *
 * <p>设计约束：
 * <ul>
 *   <li>类声明为 {@code final}，禁止继承；</li>
 *   <li>构造方法私有化，禁止实例化；</li>
 *   <li>所有字段均为 {@code public static final} 常量。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public final class SecurityConstants {

    /**
     * 自定义 CSRF 校验请求头名称。
     *
     * <p>客户端在发送状态变更请求（POST / PUT / DELETE 等）时，
     * 需在请求头中携带此字段，以通过 {@link CsrfHeaderFilter} 的校验。
     * 采用自定义请求头而非 Spring Security 默认的 Cookie-Token 配对模式，
     * 简化了前后端分离架构下的 CSRF 防护实现。
     */
    public static final String CSRF_HEADER_NAME = "X-MYAI-CSRF";

    /**
     * 自定义 CSRF 校验请求头的期望值。
     *
     * <p>当前策略为"存在即合法"的轻量校验：只要请求头存在且值为 {@code "1"}，
     * 即视为通过。未来可根据安全需求升级为动态 Token 校验策略。
     */
    public static final String CSRF_HEADER_VALUE = "1";

    /**
     * 私有构造方法，防止外部实例化此常量类。
     */
    private SecurityConstants() {
        // 工具类私有构造，禁止实例化
    }
}
