package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 登录失败状态领域模型。
 *
 * <p>封装一次密码校验失败后的账户锁定状态，由仓储的
 * {@code recordFailedLogin} 方法返回，供应用层判断是否需要抛出锁定异常。
 *
 * <p>使用 Java {@code record} 保证不可变性。
 *
 * @param failedLoginCount 当前连续失败次数（含本次）
 * @param lockedUntil      锁定截止时间（{@code null} 表示未触发锁定）
 * @author spike
 * @since 1.0.0
 */
public record LoginFailureState(
        /** 当前连续失败次数（含本次） */
        int failedLoginCount,
        /** 锁定截止时间，null 表示未触发锁定 */
        Instant lockedUntil) {

    /**
     * 判断账户是否已被锁定。
     *
     * <p>判定逻辑：{@code lockedUntil} 不为 {@code null} 即视为锁定。
     * 注意此处不检查锁定是否过期——过期判断由应用层负责
     * （在加载账户时检查 {@link LoginAccount#lockedUntil()}）。
     *
     * @return {@code true} 账户已被锁定
     */
    public boolean locked() {
        // lockedUntil 不为 null 即表示锁定已触发
        return lockedUntil != null;
    }
}
