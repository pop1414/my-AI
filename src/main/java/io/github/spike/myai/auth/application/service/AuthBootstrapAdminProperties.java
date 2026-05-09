package io.github.spike.myai.auth.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 初始管理员账号引导配置属性。
 *
 * <p>通过 {@code @ConfigurationProperties(prefix = "myai.auth.bootstrap-admin")}
 * 自动绑定以下环境变量或 YAML 配置：
 * <ul>
 *   <li>{@code MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME} —— 初始管理员用户名；</li>
 *   <li>{@code MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD} —— 初始管理员明文密码
 *       （仅启动时用于生成 BCrypt 哈希，不会以明文写入数据库）；</li>
 *   <li>{@code MYAI_AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME} —— 展示名称（可选，
 *       未配置时回退到用户名）。</li>
 * </ul>
 *
 * <p>对应 YAML 示例：
 * <pre>{@code
 * myai:
 *   auth:
 *     bootstrap-admin:
 *       username: ${MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME:}
 *       password: ${MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD:}
 *       display-name: ${MYAI_AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME:}
 * }</pre>
 *
 * <p>所有字段默认值为空字符串（{@code ""}），表示未配置。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "myai.auth.bootstrap-admin")
public class AuthBootstrapAdminProperties {

    /** 初始管理员用户名，默认空字符串表示未配置 */
    private String username = "";

    /** 初始管理员明文密码，仅用于启动时生成 BCrypt 哈希，不会以明文写入数据库 */
    private String password = "";

    /** 初始管理员展示名称，默认空字符串表示未配置 */
    private String displayName = "";

    /**
     * 判断是否已配置创建初始管理员所需的最小凭证。
     *
     * <p>用户名和密码均为非空（非 null 且非空白）时视为已配置。
     *
     * @return {@code true} 用户名与密码均非空
     */
    public boolean hasRequiredCredentials() {
        return hasText(username) && hasText(password);
    }

    /**
     * 获取去除首尾空白后的用户名。
     *
     * <p>对原始配置值做 trim 处理，防止环境变量中混入意外的空白字符。
     *
     * @return 规范化用户名，配置为 {@code null} 时返回空字符串
     */
    public String normalizedUsername() {
        return username == null ? "" : username.trim();
    }

    /**
     * 获取展示名称；未配置时回退到规范化用户名。
     *
     * <p>若 {@code displayName} 为非空，返回其 trim 后的值；
     * 否则回退到 {@link #normalizedUsername()}。
     *
     * @return 展示名称
     */
    public String resolvedDisplayName() {
        return hasText(displayName) ? displayName.trim() : normalizedUsername();
    }

    /**
     * 获取原始用户名（可能含首尾空白）。
     *
     * @return 原始用户名配置值
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取原始明文密码。
     *
     * @return 原始密码配置值
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置明文密码。
     *
     * @param password 明文密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取原始展示名称。
     *
     * @return 原始展示名称配置值
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置展示名称。
     *
     * @param displayName 展示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 判断字符串是否包含非空白字符。
     *
     * <p>用于替代 {@link org.springframework.util.StringUtils#hasText(String)}，
     * 避免引入额外依赖。
     *
     * @param value 待检查的字符串
     * @return {@code true} 字符串非 null 且包含非空白字符
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
