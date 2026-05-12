package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.UpdateManagedAccountStatusCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.UpdateManagedAccountStatusUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 更新托管账号状态应用服务。
 *
 * <p>实现 {@link UpdateManagedAccountStatusUseCase} 用例，将目标用户
 * 标记为 ACTIVE 或 DISABLED。如果目标状态与当前状态一致，则跳过更新
 * 直接返回（幂等）。
 *
 * <p>前置条件：当前用户需具备工作区管理权限。
 */
@Service
public class UpdateManagedAccountStatusApplicationService implements UpdateManagedAccountStatusUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final ManagedAccountRepository managedAccountRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param managedAccountRepository    托管账号仓储
     * @param auditEventRepository        审计事件仓储
     */
    public UpdateManagedAccountStatusApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            ManagedAccountRepository managedAccountRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.managedAccountRepository = managedAccountRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行状态更新操作。
     *
     * <p>业务流程：
     * <ol>
     *   <li>校验权限：确保当前用户是工作区管理员</li>
     *   <li>查询目标账号 + 状态白名单校验</li>
     *   <li>治理边界校验：ADMIN 不可操作 OWNER 或其他 ADMIN</li>
     *   <li>幂等判断：目标状态与当前状态一致则直接返回</li>
     *   <li>持久化：更新用户状态</li>
     *   <li>审计记录：记录状态变更事件（含前后状态）</li>
     *   <li>组装返回：构造包含新状态的结果对象</li>
     * </ol>
     *
     * @param command 更新状态命令，包含目标用户 ID 和新状态
     * @return 更新后的账号信息
     * @throws ManagedAccountNotFoundException 如果目标账号不存在
     */
    @Override
    public ManagedAccountResult handle(UpdateManagedAccountStatusCommand command) {
        // 1. 权限校验
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // 2. 查询目标账号
        ManagedAccount account = managedAccountRepository.findWorkspaceAccount(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new ManagedAccountNotFoundException(
                        "managed account not found: " + command.normalizedUserId()));
        // 2.1 治理边界校验：ADMIN 不可停用/启用 OWNER 或其他 ADMIN
        workspaceGovernanceGuard.requireCanManageManagedAccount(
                currentUser,
                account.workspaceRole());

        // 3. 解析目标状态（命令层已完成白名单校验）
        String targetStatus = command.resolvedUserStatus();

        // 4. 幂等保护：状态未变化则直接返回，避免无意义的写操作
        if (account.userStatus().equals(targetStatus)) {
            return ListManagedAccountsApplicationService.toResult(account);
        }

        // 5. 持久化：将用户状态更新为目标值
        Instant now = Instant.now();
        boolean updated = managedAccountRepository.updateUserStatus(
                currentUser.workspaceId(),
                account.userId(),
                targetStatus,
                now);
        if (!updated) {
            throw new ManagedAccountNotFoundException("managed account not found: " + command.normalizedUserId());
        }

        // 6. 审计：记录状态变更事件，详细内容包含变更前后的状态
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MANAGED_ACCOUNT_STATUS_UPDATED",
                "MANAGED_ACCOUNT",
                account.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s,"previousStatus":%s,"newStatus":%s}
                """.formatted(
                        toJsonString(account.userId()),
                        toJsonString(account.username()),
                        toJsonString(account.userStatus()),
                        toJsonString(targetStatus)),
                now));

        // 7. 构造包含新状态的结果对象（其余字段沿用原始值）
        return new ManagedAccountResult(
                account.userId(),
                account.username(),
                account.displayName(),
                targetStatus,
                account.workspaceId(),
                account.workspaceRole(),
                account.membershipStatus(),
                account.failedLoginCount(),
                account.lockedUntil());
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
