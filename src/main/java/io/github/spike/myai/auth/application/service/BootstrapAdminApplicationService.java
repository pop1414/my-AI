package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.domain.model.BootstrapAdminAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.BootstrapAdminRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始管理员账号引导服务。
 *
 * <p>实现 {@link ApplicationRunner}，在 Spring 容器启动完成后自动执行，
 * 负责在空数据库环境中创建第一个管理员账号（"零号用户"）。
 *
 * <p>执行条件（全部满足才创建）：
 * <ol>
 *   <li>环境变量/配置中提供了管理员用户名和密码；</li>
 *   <li>默认工作空间中尚无任何成员（{@code workspace_memberships} 表为空）。</li>
 * </ol>
 *
 * <p>安全考量：
 * <ul>
 *   <li>密码仅在内存中以明文存在，写入数据库前经 BCrypt 编码；</li>
 *   <li>使用随机 UUID 生成用户 ID，避免可预测的主键；</li>
 *   <li>引导账号分配 {@code WORKSPACE_OWNER} 角色，拥有最高权限；</li>
 *   <li>事务保证三表（users / local_credentials / workspace_memberships）
 *       写入的原子性。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class BootstrapAdminApplicationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminApplicationService.class);

    /** 引导管理员角色常量：工作空间所有者，拥有最高权限 */
    private static final WorkspaceRole BOOTSTRAP_ADMIN_ROLE = WorkspaceRole.WORKSPACE_OWNER;

    /** 引导管理员配置属性（用户名、密码、展示名称） */
    private final AuthBootstrapAdminProperties properties;

    /** 引导管理员仓储（出站端口），负责三表写入 */
    private final BootstrapAdminRepository bootstrapAdminRepository;

    /** 密码编码器，用于将明文密码编码为 BCrypt 哈希 */
    private final PasswordEncoder passwordEncoder;

    /** 时钟实例，便于测试时注入固定时间 */
    private final Clock clock;

    /**
     * Spring 公开构造器（生产环境入口）。
     *
     * <p>使用系统默认 UTC 时钟。
     *
     * @param properties                引导管理员配置属性
     * @param bootstrapAdminRepository  引导管理员仓储
     * @param passwordEncoder           密码编码器
     */
    @Autowired
    public BootstrapAdminApplicationService(
            AuthBootstrapAdminProperties properties,
            BootstrapAdminRepository bootstrapAdminRepository,
            PasswordEncoder passwordEncoder) {
        // 委托包级私有构造器，注入系统 UTC 时钟
        this(properties, bootstrapAdminRepository, passwordEncoder, Clock.systemUTC());
    }

    /**
     * 包级私有构造器（测试入口）。
     *
     * <p>允许测试代码注入可控的 {@link Clock} 实例。
     *
     * @param properties                引导管理员配置属性
     * @param bootstrapAdminRepository  引导管理员仓储
     * @param passwordEncoder           密码编码器
     * @param clock                     时钟实例
     */
    BootstrapAdminApplicationService(
            AuthBootstrapAdminProperties properties,
            BootstrapAdminRepository bootstrapAdminRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.bootstrapAdminRepository = bootstrapAdminRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 执行初始管理员引导逻辑。
     *
     * <p>处理流程：
     * <ol>
     *   <li>检查配置凭证是否齐全，不足则跳过并记录日志；</li>
     *   <li>查询默认工作空间成员数，已有成员则跳过（防止重复创建）；</li>
     *   <li>构建 {@link BootstrapAdminAccount} 领域对象（生成 UUID 主键、
     *       BCrypt 编码密码）；</li>
     *   <li>通过仓储原子性写入三表并返回用户 ID；</li>
     *   <li>记录创建成功的日志（含用户名、用户 ID、工作空间 ID、角色）。</li>
     * </ol>
     *
     * @param args 应用启动参数（本实现未使用）
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 步骤1：检查是否配置了管理员凭证（用户名和密码均非空）
        if (!properties.hasRequiredCredentials()) {
            log.info("Skip bootstrap admin creation because username or password is not configured.");
            return;
        }

        // 步骤2：检查默认工作空间中是否已有成员，已有则跳过
        int membershipCount = bootstrapAdminRepository.countWorkspaceMemberships(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID);
        if (membershipCount > 0) {
            log.info("Skip bootstrap admin creation because default workspace already has {} membership(s).",
                    membershipCount);
            return;
        }

        // 步骤3：构建引导管理员领域对象
        Instant now = Instant.now(clock);
        BootstrapAdminAccount account = new BootstrapAdminAccount(
                // 使用随机 UUID 作为用户主键，避免可预测性
                UUID.randomUUID().toString(),
                // 规范化用户名（去除首尾空白）
                properties.normalizedUsername(),
                // 展示名称优先使用配置值，未配置时回退到用户名
                properties.resolvedDisplayName(),
                // 将明文密码编码为 BCrypt 哈希
                passwordEncoder.encode(properties.getPassword()),
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                BOOTSTRAP_ADMIN_ROLE,
                now);

        // 步骤4：通过仓储原子性写入三表（users + local_credentials + workspace_memberships）
        String userId = bootstrapAdminRepository.saveBootstrapAdmin(account);
        // 步骤5：记录创建成功日志
        log.info("Bootstrap admin account is ready. username={}, userId={}, workspaceId={}, role={}",
                account.username(),
                userId,
                account.workspaceId(),
                account.role());
    }
}
