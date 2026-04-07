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
     * @param limit 最大返回条数
     * @return 分块列表（按 chunkIndex 升序）
     */
    List<DocumentChunkPreview> findByDocumentId(DocumentId documentId, int limit);
}

