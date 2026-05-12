package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import java.util.List;

/**
 * 查询知识库授权列表用例。
 * <p>
 * 定义查询指定知识库下所有活跃授权记录的业务边界。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验目标知识库是否存在</li>
 *   <li>查询该知识库下所有状态为 {@code ACTIVE} 的授权记录</li>
 *   <li>将领域模型转换为用例层结果对象</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface ListKnowledgeBaseGrantsUseCase {

    /**
     * 执行查询知识库授权列表用例。
     *
     * @param kbId 知识库唯一标识，不能为空或纯空白
     * @return 授权结果列表，若无授权记录则返回空列表（非 {@code null}）
     * @throws IllegalArgumentException 当 {@code kbId} 为空或纯空白时抛出
     */
    List<KnowledgeBaseGrantResult> handle(String kbId);
}
