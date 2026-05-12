package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.RemoveManagedAccountMembershipCommand;

/**
 * 移除成员关系用例。
 *
 * <p>将指定用户从当前工作区中移除（将成员状态置为 INACTIVE），
 * 如果成员已经是非活跃状态则幂等返回。
 */
public interface RemoveManagedAccountMembershipUseCase {

    /**
     * 执行移除成员关系操作。
     *
     * @param command 移除成员关系命令，包含目标用户 ID
     * @throws io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException 如果目标账号不存在
     */
    void handle(RemoveManagedAccountMembershipCommand command);
}
