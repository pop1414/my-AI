package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.Document;
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
    void index(Document document, List<String> chunks);
}

