package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.RemoveManagedAccountMembershipCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.application.usecase.RemoveManagedAccountMembershipUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 移除成员关系应用服务。
 *
 * <p>实现 {@link RemoveManagedAccountMembershipUseCase} 用例，将目标用户
 * 在当前工作区的成员关系标记为 INACTIVE。如果成员已经是非活跃状态，
 * 则直接返回不做任何变更（幂等）。
 *
 * <p>前置条件：当前用户需具备工作区管理权限。
 */
@Service
public class RemoveManagedAccountMembershipApplicationService implements RemoveManagedAccountMembershipUseCase {

    private final AuthorizationService authorizationService;
    private final ManagedAccountRepository managedAccountRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService     授权服务
     * @param managedAccountRepository 托管账号仓储
     * @param auditEventRepository     审计事件仓储
     */
    public RemoveManagedAccountMembershipApplicationService(
            AuthorizationService authorizationService,
            ManagedAccountRepository managedAccountRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.managedAccountRepository = managedAccountRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行移除成员关系操作。
     *
     * <p>业务流程：
     * <ol>
     *   <li>校验权限：确保当前用户是工作区管理员</li>
     *   <li>查询目标账号：定位当前工作区下的目标用户</li>
     *   <li>幂等判断：如果成员已是 INACTIVE 状态，直接返回</li>
     *   <li>持久化：将成员关系状态置为 INACTIVE</li>
     *   <li>审计记录：记录成员移除事件</li>
     * </ol>
     *
     * @param command 移除成员关系命令
     * @throws ManagedAccountNotFoundException 如果目标账号不存在
     */
    @Override
    public void handle(RemoveManagedAccountMembershipCommand command) {
        // 1. 权限校验
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // 2. 查询目标账号，不存在则抛出异常
        ManagedAccount account = managedAccountRepository.findWorkspaceAccount(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new ManagedAccountNotFoundException(
                        "managed account not found: " + command.normalizedUserId()));

        // 3. 幂等保护：已经是 INACTIVE 则无需操作
        if (!"ACTIVE".equals(account.membershipStatus())) {
            return;
        }

        // 4. 持久化：将成员关系标记为 INACTIVE
        Instant now = Instant.now();
        boolean updated = managedAccountRepository.deactivateMembership(
                currentUser.workspaceId(),
                account.userId(),
                now);
        if (!updated) {
            throw new ManagedAccountNotFoundException("managed account not found: " + command.normalizedUserId());
        }

        // 5. 审计：记录成员移除事件
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MANAGED_ACCOUNT_MEMBERSHIP_REMOVED",
                "WORKSPACE_MEMBERSHIP",
                account.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s}
                """.formatted(
                        toJsonString(account.userId()),
                        toJsonString(account.username())),
                now));
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
