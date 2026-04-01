package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.Optional;

/**
 * 文档仓储端口（Domain Port）。
 *
 * <p>该接口定义领域层对“文档元数据持久化”的能力需求。
 * 领域层只声明需要什么操作，不关心底层实现是 PostgreSQL、MySQL 还是内存。
 */
public interface DocumentRepository {

    /**
     * 保存文档聚合（新增或更新）。
     *
     * @param document 领域文档对象
     */
    void save(Document document);

    /**
     * 按文档 ID 查询文档聚合。
     *
     * @param documentId 文档 ID
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findById(DocumentId documentId);
}
