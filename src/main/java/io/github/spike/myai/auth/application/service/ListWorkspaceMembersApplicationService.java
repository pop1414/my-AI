package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.ListWorkspaceMembersUseCase;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询工作区成员列表应用服务。
 * <p>
 * 实现 {@link ListWorkspaceMembersUseCase} 用例，负责以下职责：
 * <ol>
 *   <li>校验当前用户（调用方）是否具备工作区管理权限</li>
 *   <li>从持久层查询当前工作区下所有活跃成员</li>
 *   <li>将领域模型转换为用例层返回结果</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class ListWorkspaceMembersApplicationService implements ListWorkspaceMembersUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 工作区成员持久化仓储 */
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService      授权服务
     * @param workspaceMemberRepository 工作区成员仓储
     */
    public ListWorkspaceMembersApplicationService(
            AuthorizationService authorizationService,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.authorizationService = authorizationService;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    /**
     * 执行查询工作区成员列表用例。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>通过授权服务获取当前用户上下文，若权限不足则抛出异常</li>
     *   <li>以当前用户所属工作区 ID 查询所有活跃成员</li>
     *   <li>逐一将领域模型映射为 {@link WorkspaceMemberResult} 结果对象</li>
     * </ol>
     *
     * @return 当前工作区活跃成员结果列表，若无成员则返回空列表
     */
    @Override
    public List<WorkspaceMemberResult> handle() {
        // Step 1: 校验当前用户的工作区管理权限，获取用户上下文
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // Step 2: 查询当前工作区所有活跃成员
        // Step 3: 将领域模型转换为用例层结果对象并收集为不可变列表
        return workspaceMemberRepository.findActiveMembers(currentUser.workspaceId()).stream()
                .map(member -> new WorkspaceMemberResult(
                        member.userId(),
                        member.username(),
                        member.displayName(),
                        member.workspaceId(),
                        member.workspaceRole(),
                        member.membershipStatus()))
                .toList();
    }
}
