package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import java.util.List;

/**
 * 查询托管账号列表用例。
 *
 * <p>返回当前工作区下所有托管账号的治理信息，
 * 按创建时间升序排列，供管理员进行账号巡检和操作。
 */
public interface ListManagedAccountsUseCase {

    /**
     * 执行查询账号列表操作。
     *
     * @return 当前工作区托管账号列表
     */
    List<ManagedAccountResult> handle();
}
