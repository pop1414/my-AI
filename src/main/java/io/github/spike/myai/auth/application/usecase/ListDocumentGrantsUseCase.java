package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import java.util.List;

/**
 * 查询文档授权列表用例。
 * <p>
 * 定义查询指定文档下所有活跃授权记录的业务边界。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验目标文档是否存在</li>
 *   <li>查询该文档下所有状态为 {@code ACTIVE} 的授权记录</li>
 *   <li>将领域模型转换为用例层结果对象</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface ListDocumentGrantsUseCase {

    /**
     * 执行查询文档授权列表用例。
     *
     * @param documentId 文档唯一标识，不能为空或纯空白
     * @return 授权结果列表，若无授权记录则返回空列表（非 {@code null}）
     * @throws IllegalArgumentException 当 {@code documentId} 为空或纯空白时抛出
     */
    List<DocumentGrantResult> handle(String documentId);
}
