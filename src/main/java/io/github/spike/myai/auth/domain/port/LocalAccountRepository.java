package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.LoginFailureState;
import java.time.Instant;
import java.util.Optional;

/**
 * 本地账户仓储端口（出站端口）。
 *
 * <p>定义本地账户查询和登录状态更新的持久化契约，位于六边形架构的领域层。
 * 应用服务仅依赖此接口，不感知底层 SQL 实现细节。
 *
 * <p>当前唯一实现：
 * {@link io.github.spike.myai.auth.infrastructure.persistence.JdbcLocalAccountRepository}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface LocalAccountRepository {

    /**
     * 按用户名查找登录账户。
     *
     * <p>返回的账户聚合了用户信息、密码哈希、工作空间成员资格
     * 及当前锁定状态，供应用层执行完整的登录校验。
     *
     * @param username 用户名
     * @return 包含完整登录信息的账户对象，若不存在则为 {@link Optional#empty()}
     */
    Optional<LoginAccount> findByUsername(String username);

    /**
     * 记录一次登录失败，并返回最新的锁定状态。
     *
     * <p>实现需原子性地完成以下操作：
     * <ol>
     *   <li>递增失败计数器；</li>
     *   <li>若失败次数达到 {@code maxFailedAttempts} 阈值，设置锁定截止时间；</li>
     *   <li>更新最后一次失败时间戳；</li>
     *   <li>返回最新的 {@link LoginFailureState}。</li>
     * </ol>
     *
     * @param userId             用户 ID
     * @param failedAt           失败时间戳
     * @param maxFailedAttempts  触发锁定的最大失败次数阈值
     * @param lockUntil          计算好的锁定截止时间（当前时间 + 锁定时长）
     * @return 最新的登录失败状态（含锁定标记）
     */
    LoginFailureState recordFailedLogin(
            String userId,
            Instant failedAt,
            int maxFailedAttempts,
            Instant lockUntil);

    /**
     * 记录一次登录成功。
     *
     * <p>实现需原子性地完成：
     * <ol>
     *   <li>将失败计数器重置为 0；</li>
     *   <li>清除锁定截止时间（设为 {@code null}）；</li>
     *   <li>更新最后登录时间戳。</li>
     * </ol>
     *
     * @param userId  用户 ID
     * @param loginAt 登录成功时间戳
     */
    void recordSuccessfulLogin(String userId, Instant loginAt);
}
