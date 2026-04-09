package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.List;

/**
 * 文档向量索引端口。
 */
public interface DocumentVectorIndexer {

    /**
     * 将文档分块写入向量索引。
     *
     * @param document 文档资产
     * @param chunks 分块结果
     */
    void index(Document document, List<DocumentChunk> chunks);

    /**
     * 删除指定文档与分块版本的向量。
     *
     * <p>用于 reprocess 触发前清理旧版本向量，避免旧向量继续参与检索。
     *
     * @param documentId 文档资产 ID
     * @param splitVersion 分块版本
     */
    void deleteByDocumentIdAndSplitVersion(DocumentId documentId, String splitVersion);
}
