package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.CreateManagedAccountCommand;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;

/**
 * 创建托管账号用例。
 *
 * <p>由工作区管理员发起，在当前工作区下创建一个新的本地托管账号，
 * 包含用户基础信息、登录凭据和工作区成员关系。
 */
public interface CreateManagedAccountUseCase {

    /**
     * 执行创建账号操作。
     *
     * @param command 创建账号命令
     * @return 创建成功的账号信息
     * @throws io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException 如果用户名已存在
     */
    ManagedAccountResult handle(CreateManagedAccountCommand command);
}
