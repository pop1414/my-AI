package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.ResetManagedAccountPasswordCommand;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;

/**
 * 重置托管账号密码用例。
 *
 * <p>由工作区管理员发起，重置指定托管账号的登录密码，
 * 同时清除该账号的登录锁定状态（failedLoginCount 归零、lockedUntil 置空）。
 */
public interface ResetManagedAccountPasswordUseCase {

    /**
     * 执行密码重置操作。
     *
     * @param command 重置密码命令，包含目标用户 ID 和新密码
     * @return 重置后的账号信息
     * @throws io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException 如果目标账号不存在
     */
    ManagedAccountResult handle(ResetManagedAccountPasswordCommand command);
}
