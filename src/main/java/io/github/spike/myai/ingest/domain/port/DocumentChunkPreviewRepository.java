package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.List;

/**
 * 文档分块预览查询端口（Domain Port / Read-Only Repository）。
 *
 * <p>该接口是六边形架构中的<b>领域端口</b>，专门服务于文档分块预览的<b>读场景</b>。
 * 提供按文档 ID + 分块版本查询分块详情与计数的能力，
 * 用于前端调试页面的分块预览功能。
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentChunkPreviewRepository {

    /**
     * 按文档资产 ID 查询分块预览数据（分页）。
     *
     * <p>结果按 {@code chunkIndex} 升序排列，保证预览顺序与原文一致。
     * 查询限定在指定工作区范围内，避免跨工作区数据泄露。
     *
     * @param workspaceId  工作区标识
     * @param documentId   文档资产 ID
     * @param splitVersion 分块版本（用于筛选当前文档版本的向量）
     * @param limit        最大返回条数
     * @param offset       起始偏移（用于分页/抽样）
     * @return 分块列表（按 chunkIndex 升序）
     */
    List<DocumentChunkPreview> findByDocumentId(
            String workspaceId,
            DocumentId documentId,
            String splitVersion,
            int limit,
            int offset);

    /**
     * 查询指定文档 + 分块版本的总分块数。
     *
     * <p>用于前端展示分块总数与分页计算。
     *
     * @param workspaceId  工作区标识
     * @param documentId   文档资产 ID
     * @param splitVersion 分块版本
     * @return 分块总数（无数据时返回 0）
     */
    int countByDocumentId(String workspaceId, DocumentId documentId, String splitVersion);
}
