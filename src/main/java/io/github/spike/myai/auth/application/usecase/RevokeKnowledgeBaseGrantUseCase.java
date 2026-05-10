package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.RevokeKnowledgeBaseGrantCommand;

/**
 * 回收知识库授权用例。
 * <p>
 * 定义移除指定用户对某知识库访问权限的业务边界。
 * 回收操作为软删除：将授权状态从 {@code ACTIVE} 变更为 {@code DISABLED}，
 * 保留审计记录而非物理删除。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验目标知识库是否存在</li>
 *   <li>查找目标活跃授权记录</li>
 *   <li>执行软删除（状态变更为 DISABLED）</li>
 *   <li>记录回收操作的审计事件</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface RevokeKnowledgeBaseGrantUseCase {

    /**
     * 执行回收知识库授权用例。
     *
     * @param command 回收命令，包含知识库 ID 和目标用户 ID，不能为 {@code null}
     * @throws io.github.spike.myai.auth.application.exception.KnowledgeBaseGrantNotFoundException 当目标授权记录不存在时抛出
     * @throws IllegalArgumentException                                               当命令中任一必填字段为空时抛出
     */
    void handle(RevokeKnowledgeBaseGrantCommand command);
}
