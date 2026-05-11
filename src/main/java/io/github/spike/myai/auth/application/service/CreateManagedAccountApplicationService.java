package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.CreateManagedAccountCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.CreateManagedAccountUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 创建托管账号应用服务。
 *
 * <p>实现 {@link CreateManagedAccountUseCase} 用例，完成以下业务步骤：
 * <ol>
 *   <li>权限校验：当前用户需为工作区管理员</li>
 *   <li>冲突检测：检查用户名是否已被占用</li>
 *   <li>密码编码：使用 BCrypt 对明文密码做哈希处理</li>
 *   <li>持久化：通过仓储层创建用户、凭据和成员关系</li>
 *   <li>审计记录：记录 MANAGED_ACCOUNT_CREATED 审计事件</li>
 * </ol>
 */
@Service
public class CreateManagedAccountApplicationService implements CreateManagedAccountUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final ManagedAccountRepository managedAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param managedAccountRepository    托管账号仓储
     * @param passwordEncoder             密码编码器（BCrypt）
     * @param auditEventRepository        审计事件仓储
     */
    public CreateManagedAccountApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            ManagedAccountRepository managedAccountRepository,
            PasswordEncoder passwordEncoder,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.managedAccountRepository = managedAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行创建账号操作。
     *
     * @param command 创建账号命令
     * @return 创建成功的账号信息
     * @throws ManagedAccountUsernameConflictException 如果用户名已存在
     */
    @Override
    public ManagedAccountResult handle(CreateManagedAccountCommand command) {
        // 1. 权限校验：确保当前用户是工作区管理员
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // 1.1 治理边界校验：ADMIN 不可创建 OWNER/ADMIN 账号
        workspaceGovernanceGuard.requireCanCreateManagedAccount(
                currentUser,
                command.resolvedWorkspaceRole());

        // 2. 冲突检测：检查用户名是否已被占用
        if (managedAccountRepository.existsUsername(command.normalizedUsername())) {
            throw new ManagedAccountUsernameConflictException("username already exists: " + command.normalizedUsername());
        }

        // 3. 持久化：创建用户、本地凭据和工作区成员关系（仓储层保证事务性）
        Instant now = Instant.now();
        ManagedAccount created = managedAccountRepository.createAccount(
                currentUser.workspaceId(),
                command.normalizedUsername(),
                command.normalizedDisplayName(),
                passwordEncoder.encode(command.normalizedPassword()),
                command.resolvedWorkspaceRole(),
                now);

        // 4. 审计：记录账号创建事件，详细内容以 JSON 格式存储
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MANAGED_ACCOUNT_CREATED",
                "MANAGED_ACCOUNT",
                created.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s,"workspaceRole":%s}
                """.formatted(
                        toJsonString(created.userId()),
                        toJsonString(created.username()),
                        toJsonString(created.workspaceRole().name())),
                now));

        // 5. 将领域模型转换为用例结果并返回
        return ListManagedAccountsApplicationService.toResult(created);
    }

    /**
     * 将字符串包装为 JSON 字符串值（转义特殊字符）。
     *
     * @param value 原始字符串
     * @return JSON 字符串值（含双引号包裹）
     */
    private static String toJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
