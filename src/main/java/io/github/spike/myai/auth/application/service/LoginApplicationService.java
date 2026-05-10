package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.LoginCommand;
import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.application.usecase.LoginUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.LoginFailureState;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.LocalAccountRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录应用服务，实现 {@link LoginUseCase} 接口。
 *
 * <p>作为应用层的核心编排服务，负责协调领域仓储和密码编码器，
 * 执行完整的登录业务逻辑。位于六边形架构的应用层，
 * 依赖领域层的端口接口（{@link LocalAccountRepository}、{@link AuditEventRepository}），
 * 不依赖具体持久化实现。
 *
 * <p>核心流程：
 * <ol>
 *   <li>参数校验（用户名/密码非空）；</li>
 *   <li>根据用户名查找本地账户；</li>
 *   <li>依次校验：账户禁用 → 账户锁定 → 密码匹配；</li>
 *   <li>密码匹配时记录成功登录，返回用户信息；</li>
 *   <li>密码不匹配时递增失败计数器，达到阈值则锁定账户；</li>
 *   <li>所有异常/成功路径均写入审计日志。</li>
 * </ol>
 *
 * <p>安全设计要点：
 * <ul>
 *   <li>用户不存在和密码错误返回相同的错误信息（{@code invalid username or password}），
 *       防止用户名枚举攻击；</li>
 *   <li>账户锁定在密码比对之前检查，避免锁定期间的无效密码尝试触发额外的失败计数；</li>
 *   <li>通过构造器注入的 {@link Clock} 参数实现时间可控，便于单元测试。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class LoginApplicationService implements LoginUseCase {

    /** 本地账户仓储（出站端口），由基础设施层实现 */
    private final LocalAccountRepository localAccountRepository;

    /** 审计事件仓储（出站端口），用于记录安全审计日志 */
    private final AuditEventRepository auditEventRepository;

    /** 密码编码器，用于比对明文密码与数据库中的哈希值 */
    private final PasswordEncoder passwordEncoder;

    /** 认证安全配置属性（最大失败次数、锁定时长等） */
    private final AuthSecurityProperties properties;

    /** 时钟实例，便于测试时注入固定时间 */
    private final Clock clock;

    /**
     * Spring 公开构造器（生产环境入口）。
     *
     * <p>使用系统默认 UTC 时钟，由 Spring 容器通过 {@code @Autowired} 调用。
     *
     * @param localAccountRepository 本地账户仓储
     * @param auditEventRepository   审计事件仓储
     * @param passwordEncoder        密码编码器
     * @param properties             认证安全配置属性
     */
    @Autowired
    public LoginApplicationService(
            LocalAccountRepository localAccountRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            AuthSecurityProperties properties) {
        // 委托包级私有构造器，注入系统 UTC 时钟
        this(localAccountRepository, auditEventRepository, passwordEncoder, properties, Clock.systemUTC());
    }

    /**
     * 包级私有构造器（测试入口）。
     *
     * <p>允许测试代码注入可控的 {@link Clock} 实例（如 {@code Clock.fixed}），
     * 以实现时间依赖逻辑的可重复验证。
     *
     * @param localAccountRepository 本地账户仓储
     * @param auditEventRepository   审计事件仓储
     * @param passwordEncoder        密码编码器
     * @param properties             认证安全配置属性
     * @param clock                  时钟实例（生产环境为系统时钟，测试环境可注入固定时钟）
     */
    LoginApplicationService(
            LocalAccountRepository localAccountRepository,
            AuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            AuthSecurityProperties properties,
            Clock clock) {
        this.localAccountRepository = localAccountRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 执行登录业务逻辑。
     *
     * <p>处理流程分阶段进行，任一阶段失败即终止并抛出对应异常：
     *
     * <p><strong>第一阶段：参数校验</strong><br>
     * 检查命令对象及用户名密码非空。空值直接返回通用凭证错误，
     * 防止后续 NPE 且不暴露内部校验细节。
     *
     * <p><strong>第二阶段：账户查询</strong><br>
     * 按用户名查找本地账户。若不存在，记录匿名审计日志后抛出认证异常，
     * 异常信息与密码错误一致，防止用户名枚举。
     *
     * <p><strong>第三阶段：状态校验</strong><br>
     * 依次检查账户是否禁用（{@code userStatus != ACTIVE} 或
     * {@code membershipStatus != ACTIVE}）和是否锁定
     * （{@code lockedUntil} 在当前时间之后）。
     *
     * <p><strong>第四阶段：密码校验</strong><br>
     * 使用 BCrypt 比对明文与哈希。匹配则记录成功登录并返回用户结果；
     * 不匹配则记录失败计数。若失败次数达到阈值，账户被锁定并抛出
     * {@link LockedException}；未达阈值则抛出 {@link BadCredentialsException}。
     *
     * @param command 登录命令对象，包含用户名和明文密码
     * @return 登录成功后的当前用户信息
     * @throws BadCredentialsException 凭证无效（密码错误或用户不存在）
     * @throws DisabledException       账户已禁用
     * @throws LockedException         账户已锁定（连续登录失败超限）
     */
    @Override
    public CurrentUserResult handle(LoginCommand command) {
        // ---------- 第一阶段：参数校验 ----------
        // 命令对象为 null 或用户名/密码为空，直接拒绝
        if (command == null || isBlank(command.username()) || isBlank(command.password())) {
            throw new BadCredentialsException("username and password are required");
        }

        // 去除用户名首尾空白，防止空白字符绕过查询
        String username = command.username().trim();
        // 获取当前时间戳（可通过 Clock 注入控制，便于测试）
        Instant now = Instant.now(clock);

        // ---------- 第二阶段：账户查询 ----------
        // 按用户名查找本地账户，不存在则记录匿名审计并抛出异常
        LoginAccount account = localAccountRepository.findByUsername(username).orElse(null);
        if (account == null) {
            // 用户不存在时使用 null 作为 userId，记录匿名审计日志
            auditLoginFailure(null, username, "BAD_CREDENTIALS", now);
            // 异常信息与密码错误一致，防止用户名枚举攻击
            throw new BadCredentialsException("invalid username or password");
        }

        // ---------- 第三阶段：状态校验 ----------
        // 检查账户是否被禁用（用户状态或成员资格状态非 ACTIVE）
        rejectIfDisabled(account, now);
        // 检查账户是否处于锁定期（lockedUntil 未过期）
        rejectIfLocked(account, now);

        // ---------- 第四阶段：密码校验 ----------
        // 使用 BCrypt 比对用户输入的明文密码与数据库中的哈希值
        if (!passwordEncoder.matches(command.password(), account.passwordHash())) {
            // 密码不匹配：记录失败登录（含递增失败计数器），并返回锁定状态
            LoginFailureState failureState = localAccountRepository.recordFailedLogin(
                    account.userId(),
                    now,
                    properties.getMaxFailedAttempts(),
                    now.plus(properties.getLockDuration()));
            // 若失败次数达到阈值，账户被锁定
            if (failureState.locked()) {
                auditLoginFailure(account, "ACCOUNT_LOCKED", now);
                throw new LockedException(buildLockedMessage(failureState.lockedUntil()));
            }
            // 未达阈值，记录密码错误审计并返回通用认证异常
            auditLoginFailure(account, "BAD_CREDENTIALS", now);
            throw new BadCredentialsException("invalid username or password");
        }

        // ---------- 登录成功 ----------
        // 重置失败计数器，记录成功登录时间
        localAccountRepository.recordSuccessfulLogin(account.userId(), now);
        // 写入审计日志（成功事件）
        auditEventRepository.save(AuditEvent.success(
                account.workspaceId(),
                account.userId(),
                account.username(),
                "LOGIN_SUCCESS",
                now));
        // 将领域对象转换为应用层结果对象并返回
        return toResult(account);
    }

    /**
     * 检查账户是否被禁用，若禁用则记录审计并抛出异常。
     *
     * <p>两个维度均为 ACTIVE 才视为正常：
     * <ul>
     *   <li>{@code userStatus} —— 用户级别的状态（如被管理员手动禁用）；</li>
     *   <li>{@code membershipStatus} —— 成员资格状态（如被移出工作空间）。</li>
     * </ul>
     *
     * @param account 登录账户
     * @param now     当前时间戳
     * @throws DisabledException 当任一状态非 ACTIVE 时抛出
     */
    private void rejectIfDisabled(LoginAccount account, Instant now) {
        // 用户状态和成员资格状态任一非 ACTIVE 即视为禁用
        if (!"ACTIVE".equals(account.userStatus()) || !"ACTIVE".equals(account.membershipStatus())) {
            // 记录禁用审计日志
            auditLoginFailure(account, "ACCOUNT_DISABLED", now);
            // 抛出禁用异常，由全局异常处理器映射为 HTTP 403
            throw new DisabledException("account is disabled");
        }
    }

    /**
     * 检查账户是否处于锁定状态，若锁定则记录审计并抛出异常。
     *
     * <p>锁定判断依据：{@code lockedUntil} 不为 {@code null}
     * 且其时间戳在当前时间之后（即锁定期尚未过期）。
     *
     * @param account 登录账户
     * @param now     当前时间戳
     * @throws LockedException 当账户处于锁定状态时抛出
     */
    private void rejectIfLocked(LoginAccount account, Instant now) {
        // lockedUntil 非空且在当前时间之后，表示账户仍被锁定
        if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
            // 记录锁定审计日志
            auditLoginFailure(account, "ACCOUNT_LOCKED", now);
            // 抛出锁定异常，由全局异常处理器映射为 HTTP 403
            throw new LockedException(buildLockedMessage(account.lockedUntil()));
        }
    }

    private static String buildLockedMessage(Instant lockedUntil) {
        return lockedUntil == null
                ? "account is locked"
                : "account is locked until " + lockedUntil;
    }

    /**
     * 记录已知账户的登录失败审计日志。
     *
     * <p>当 {@link LoginAccount} 对象可用时调用此重载，
     * 审计事件中会包含工作空间 ID 等完整上下文信息。
     *
     * @param account    登录账户（非 {@code null}）
     * @param reason     失败原因代码（如 {@code BAD_CREDENTIALS}、{@code ACCOUNT_LOCKED}）
     * @param occurredAt 事件发生时间
     */
    private void auditLoginFailure(LoginAccount account, String reason, Instant occurredAt) {
        auditEventRepository.save(AuditEvent.failure(
                account.workspaceId(),
                account.userId(),
                account.username(),
                "LOGIN_FAILURE",
                reason,
                occurredAt));
    }

    /**
     * 记录未知账户（用户不存在）的登录失败审计日志。
     *
     * <p>当用户不存在时，无法获取 {@link LoginAccount} 对象，
     * 使用此重载以用户名和 {@code null} 工作空间 ID 记录匿名审计事件。
     *
     * @param userId     用户 ID（通常为 {@code null}，因用户不存在）
     * @param username   尝试登录的用户名
     * @param reason     失败原因代码（如 {@code BAD_CREDENTIALS}）
     * @param occurredAt 事件发生时间
     */
    private void auditLoginFailure(String userId, String username, String reason, Instant occurredAt) {
        auditEventRepository.save(AuditEvent.failure(
                null,
                userId,
                username,
                "LOGIN_FAILURE",
                reason,
                occurredAt));
    }

    /**
     * 将领域账户对象转换为应用层结果对象。
     *
     * @param account 登录成功的领域账户
     * @return 应用层当前用户结果
     */
    private static CurrentUserResult toResult(LoginAccount account) {
        return new CurrentUserResult(
                account.userId(),
                account.username(),
                account.displayName(),
                account.workspaceId(),
                account.workspaceRole());
    }

    /**
     * 判断字符串是否为 {@code null} 或空白。
     *
     * <p>使用 {@link String#isBlank()} 判定空白，
     * 不仅检查 {@code null} 和空字符串，还排除全空白字符（空格、制表符等）。
     *
     * @param value 待检查的字符串
     * @return {@code true} 如果字符串为 {@code null} 或仅含空白字符
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
