package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.ResetManagedAccountPasswordCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.ResetManagedAccountPasswordUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 重置托管账号密码应用服务。
 *
 * <p>实现 {@link ResetManagedAccountPasswordUseCase} 用例，完成以下操作：
 * <ol>
 *   <li>权限校验：当前用户需为工作区管理员</li>
 *   <li>查询目标账号：定位当前工作区下的目标用户</li>
 *   <li>密码编码：使用 BCrypt 对新密码做哈希处理</li>
 *   <li>重置密码：更新凭据中的密码哈希，同时清除登录锁定状态</li>
 *   <li>审计记录：记录 MANAGE_ACCOUNT_PASSWORD_RESET 审计事件</li>
 *   <li>回查返回：重新查询最新账号信息并返回</li>
 * </ol>
 */
@Service
public class ResetManagedAccountPasswordApplicationService implements ResetManagedAccountPasswordUseCase {

    private final AuthorizationService authorizationService;
    private final ManagedAccountRepository managedAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService     授权服务
     * @param managedAccountRepository 托管账号仓储
     * @param passwordEncoder          密码编码器（BCrypt）
     * @param auditEventRepository     审计事件仓储
     */
    public ResetManagedAccountPasswordApplicationService(
            AuthorizationService authorizationService,
            ManagedAccountRepository managedAccountRepository,
            PasswordEncoder passwordEncoder,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.managedAccountRepository = managedAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行密码重置操作。
     *
     * @param command 重置密码命令，包含目标用户 ID 和新密码
     * @return 重置后的账号信息（锁定状态已清零）
     * @throws ManagedAccountNotFoundException 如果目标账号不存在
     */
    @Override
    public ManagedAccountResult handle(ResetManagedAccountPasswordCommand command) {
        // 1. 权限校验
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // 2. 查询目标账号
        ManagedAccount account = managedAccountRepository.findWorkspaceAccount(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new ManagedAccountNotFoundException(
                        "managed account not found: " + command.normalizedUserId()));

        // 3. 重置密码：更新密码哈希 + 清除锁定状态（failedLoginCount 归零、lockedUntil 置空）
        Instant now = Instant.now();
        boolean updated = managedAccountRepository.resetPassword(
                currentUser.workspaceId(),
                account.userId(),
                passwordEncoder.encode(command.normalizedPassword()),
                now);
        if (!updated) {
            throw new ManagedAccountNotFoundException("managed account not found: " + command.normalizedUserId());
        }

        // 4. 审计：记录密码重置事件
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MANAGED_ACCOUNT_PASSWORD_RESET",
                "MANAGED_ACCOUNT",
                account.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s}
                """.formatted(
                        toJsonString(account.userId()),
                        toJsonString(account.username())),
                now));

        // 5. 回查最新数据（包含清零后的锁定状态），转换为结果返回
        ManagedAccount refreshedAccount = managedAccountRepository.findWorkspaceAccount(
                        currentUser.workspaceId(),
                        account.userId())
                .orElseThrow(() -> new ManagedAccountNotFoundException("managed account not found: " + command.normalizedUserId()));
        return ListManagedAccountsApplicationService.toResult(refreshedAccount);
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
