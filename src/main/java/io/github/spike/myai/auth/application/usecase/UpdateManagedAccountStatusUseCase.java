package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.UpdateManagedAccountStatusCommand;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;

/**
 * 更新托管账号状态用例。
 *
 * <p>由工作区管理员发起，将指定托管账号标记为 ACTIVE 或 DISABLED，
 * 如果目标状态与当前状态一致则直接返回。
 */
public interface UpdateManagedAccountStatusUseCase {

    /**
     * 执行状态更新操作。
     *
     * @param command 更新状态命令，包含目标用户 ID 和新状态
     * @return 更新后的账号信息
     * @throws io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException 如果目标账号不存在
     */
    ManagedAccountResult handle(UpdateManagedAccountStatusCommand command);
}
