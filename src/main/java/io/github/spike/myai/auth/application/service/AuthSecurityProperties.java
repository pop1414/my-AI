package io.github.spike.myai.auth.application.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证安全配置属性类。
 *
 * <p>通过 {@code @ConfigurationProperties(prefix = "myai.auth.security")}
 * 将配置文件中的安全参数自动绑定到本类字段，实现类型安全的配置管理。
 *
 * <p>对应 YAML 配置示例：
 * <pre>{@code
 * myai:
 *   auth:
 *     security:
 *       max-failed-attempts: 5
 *       lock-duration: 1m
 * }</pre>
 *
 * <p>属性说明：
 * <ul>
 *   <li>{@code maxFailedAttempts} —— 允许的最大连续登录失败次数，
 *       超过此次数后账户将被临时锁定；</li>
 *   <li>{@code lockDuration} —— 账户锁定时长，超时后自动解锁。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Component
@ConfigurationProperties(prefix = "myai.auth.security")
public class AuthSecurityProperties {

    /**
     * 最大连续登录失败次数。
     *
     * <p>默认值：{@code 5}。达到此次数后，账户将被锁定，
     * 锁定期间即使密码正确也无法登录。
     */
    private int maxFailedAttempts = 5;

    /**
     * 账户锁定时长。
     *
     * <p>默认值：{@code 15} 分钟。从最后一次失败登录时间起算，
     * 超过此时长后锁定自动解除。
     */
    private Duration lockDuration = Duration.ofMinutes(1);

    /**
     * 获取最大连续登录失败次数。
     *
     * @return 最大失败次数
     */
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    /**
     * 设置最大连续登录失败次数。
     *
     * @param maxFailedAttempts 最大失败次数
     */
    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    /**
     * 获取账户锁定时长。
     *
     * @return 锁定时长
     */
    public Duration getLockDuration() {
        return lockDuration;
    }

    /**
     * 设置账户锁定时长。
     *
     * @param lockDuration 锁定时长
     */
    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }
}
