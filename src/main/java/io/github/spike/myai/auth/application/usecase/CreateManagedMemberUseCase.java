package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.CreateManagedMemberCommand;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;

/**
 * 创建工作区成员并初始化授权用例。
 */
public interface CreateManagedMemberUseCase {

    ManagedAccountResult handle(CreateManagedMemberCommand command);
}
