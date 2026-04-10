package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.List;

/**
 * 文档分块预览查询端口。
 */
public interface DocumentChunkPreviewRepository {

    /**
     * 按文档资产 ID 查询分块预览数据。
     *
     * @param documentId 文档资产 ID
     * @param splitVersion 分块版本（用于筛选当前文档版本的向量）
     * @param limit 最大返回条数
     * @param offset 起始偏移（用于分页/抽样）
     * @return 分块列表（按 chunkIndex 升序）
     */
    List<DocumentChunkPreview> findByDocumentId(DocumentId documentId, String splitVersion, int limit, int offset);

    /**
     * 查询指定文档 + 分块版本的总分块数。
     *
     * @param documentId 文档资产 ID
     * @param splitVersion 分块版本
     * @return 分块总数
     */
    int countByDocumentId(DocumentId documentId, String splitVersion);
}
