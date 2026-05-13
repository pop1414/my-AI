package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.ListDocumentVersionsQuery;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryResult;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;

/**
 * 查询指定文档版本历史的用例。
 *
 * <p>该用例封装了"按文档 ID 查看其版本变更历史"的完整业务能力，
 * 由 Interface 层的 REST 控制器调用，应用服务层负责实现。
 *
 * <p>前置条件：
 * <ul>
 *   <li>当前用户已认证（通过安全上下文获取）；</li>
 *   <li>目标文档在当前用户的工作区内存在；</li>
 *   <li>当前用户对该文档具备读取权限。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface ListDocumentVersionsUseCase {

    /**
     * 执行版本历史查询。
     *
     * @param query 包含目标文档 ID 的查询对象（不可为空）
     * @return 文档版本历史聚合结果，按版本号降序排列
     * @throws DocumentNotFoundException 当目标文档不存在或用户无权限时
     * @throws IllegalArgumentException  当 query 参数校验失败时
     */
    DocumentVersionHistoryResult handle(ListDocumentVersionsQuery query);
}
