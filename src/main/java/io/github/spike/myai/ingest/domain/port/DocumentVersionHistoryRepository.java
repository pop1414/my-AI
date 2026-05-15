package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistory;

/**
 * 文档版本历史只读仓储端口。
 *
 * <p>该端口定义在领域层，由基础设施层的 JDBC 实现负责具体查询逻辑。
 * 仅暴露版本历史的查询能力，不包含写入/更新操作。
 *
 * <p>实现类必须保证：
 * <ul>
 *   <li>使用工作区 ID 作为租户隔离条件，防止跨工作区数据泄露。</li>
 * </ul>
 */
public interface DocumentVersionHistoryRepository {

    /**
     * 按工作区与 documentId 查询版本历史。
     *
     * <p>如果指定文档 ID 在当前工作区内不存在任何版本记录，
     * 返回空版本历史而非 null，避免上层需要空值判断。
     *
     * @param workspaceId 工作区标识（租户隔离条件）
     * @param documentId  文档资产 ID（强类型标识）
     * @return 版本历史读模型，保证非 null（无记录时 items 为空列表）
     */
    DocumentVersionHistory findByDocumentId(
            String workspaceId,
            DocumentId documentId);
}
