package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;

/**
 * 调整工作区成员角色用例。
 * <p>
 * 定义更新指定工作区成员角色的业务边界。
 * 通过 {@link UpdateWorkspaceMemberRoleCommand} 命令对象传入目标用户 ID 和新角色，
 * 用例内部完成权限校验、成员存在性检查及角色变更操作。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>根据命令中的用户 ID 查找目标活跃成员</li>
 *   <li>执行幂等判断：若角色未变更则直接返回</li>
 *   <li>通过仓储层原子更新成员角色</li>
 *   <li>记录角色变更的审计事件</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface UpdateWorkspaceMemberRoleUseCase {

    /**
     * 执行更新工作区成员角色用例。
     * <p>
     * 接收包含目标用户 ID 和新角色的命令对象，完成权限校验、
     * 成员查找、幂等判断、角色更新及审计记录全流程。
     *
     * @param command 角色更新命令，包含目标用户 ID 和新角色枚举值，不能为 {@code null}
     * @return 更新后的工作区成员结果（含新角色信息）
     * @throws io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException 当目标成员不存在或已非活跃时抛出
     * @throws IllegalArgumentException                                         当命令中的角色字符串无法解析为有效枚举值时抛出
     */
    WorkspaceMemberResult handle(UpdateWorkspaceMemberRoleCommand command);
}
