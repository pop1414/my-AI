package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.ListManagedAccountsUseCase;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询托管账号列表应用服务。
 *
 * <p>实现 {@link ListManagedAccountsUseCase} 用例，负责对当前工作区的
 * 托管账号进行查询，并将领域模型转换为用例层的结果对象。
 *
 * <p>前置条件：当前用户需具备工作区管理权限（由 AuthorizationService 校验）。
 */
@Service
public class ListManagedAccountsApplicationService implements ListManagedAccountsUseCase {

    private final AuthorizationService authorizationService;
    private final ManagedAccountRepository managedAccountRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService     授权服务，用于校验当前用户权限
     * @param managedAccountRepository 托管账号仓储
     */
    public ListManagedAccountsApplicationService(
            AuthorizationService authorizationService,
            ManagedAccountRepository managedAccountRepository) {
        this.authorizationService = authorizationService;
        this.managedAccountRepository = managedAccountRepository;
    }

    /**
     * 执行查询账号列表操作。
     *
     * <p>流程：
     * <ol>
     *   <li>校验当前用户是否为工作区管理员</li>
     *   <li>从仓储层查询当前工作区的所有托管账号</li>
     *   <li>将领域模型逐一转换为用例层结果对象</li>
     * </ol>
     *
     * @return 当前工作区托管账号列表
     */
    @Override
    public List<ManagedAccountResult> handle() {
        // 校验权限：仅工作区管理员可查询
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // 查询当前工作区下所有托管账号，并转换为结果对象
        return managedAccountRepository.findWorkspaceAccounts(currentUser.workspaceId()).stream()
                .map(ListManagedAccountsApplicationService::toResult)
                .toList();
    }

    /**
     * 将领域读模型转换为用例层结果对象。
     *
     * <p>这是一对一的字段映射，不做业务加工。
     *
     * @param account 领域层的托管账号读模型
     * @return 用例层结果
     */
    static ManagedAccountResult toResult(ManagedAccount account) {
        return new ManagedAccountResult(
                account.userId(),
                account.username(),
                account.displayName(),
                account.userStatus(),
                account.workspaceId(),
                account.workspaceRole(),
                account.membershipStatus(),
                account.failedLoginCount(),
                account.lockedUntil());
    }
}
