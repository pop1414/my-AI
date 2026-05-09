package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import java.util.List;

/**
 * 查询工作区成员列表用例。
 * <p>
 * 定义查询当前工作区所有活跃成员的业务边界。
 * 调用方无需传入任何参数，用例内部通过当前用户上下文自动确定目标工作区。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>从持久层查询当前工作区下所有活跃成员</li>
 *   <li>将领域模型转换为用例层返回结果</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface ListWorkspaceMembersUseCase {

    /**
     * 执行查询工作区成员列表用例。
     * <p>
     * 基于当前登录用户的上下文信息，查询其所属工作区下所有状态为活跃的成员。
     * 若当前用户无管理权限，实现类应抛出相应授权异常。
     *
     * @return 当前工作区活跃成员结果列表，若无成员则返回空列表（非 {@code null}）
     */
    List<WorkspaceMemberResult> handle();
}
