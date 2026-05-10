package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.UpsertKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;

/**
 * 授予或更新知识库授权用例。
 * <p>
 * 定义向指定用户授予（或更新）知识库访问权限的业务边界。
 * 采用 Upsert 语义：若授权记录已存在则更新角色，否则新建授权记录。
 * 若目标角色与当前角色一致则幂等返回，避免无意义写操作。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验目标知识库是否存在</li>
 *   <li>校验目标用户是否为活跃工作区成员</li>
 *   <li>执行幂等判断：角色未变更则直接返回</li>
 *   <li>执行 Upsert 操作并记录审计事件</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface UpsertKnowledgeBaseGrantUseCase {

    /**
     * 执行授予或更新知识库授权用例。
     *
     * @param command 授权命令，包含知识库 ID、用户 ID 和目标角色，不能为 {@code null}
     * @return 操作后的授权结果（含新角色信息）
     * @throws io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException 当目标用户不是活跃工作区成员时抛出
     * @throws IllegalArgumentException                                              当命令中任一必填字段为空或角色无效时抛出
     */
    KnowledgeBaseGrantResult handle(UpsertKnowledgeBaseGrantCommand command);
}
