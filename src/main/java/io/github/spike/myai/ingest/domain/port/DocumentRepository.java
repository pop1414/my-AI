package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.time.Instant;
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

    /**
     * 按知识库和文件哈希查询文档，用于上传受理幂等。
     *
     * @param kbId 知识库 ID
     * @param fileHash 文件内容哈希（SHA-256 十六进制）
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findByKbIdAndFileHash(String kbId, String fileHash);

    /**
     * 按状态查找最早创建的一条文档资产记录。
     *
     * @param status 文档状态
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findOldestByStatus(UploadStatus status);

    /**
     * 条件更新状态（CAS）。
     *
     * <p>仅当数据库当前状态与 expectedStatus 一致时才会更新成功。
     *
     * @param documentId 文档资产 ID
     * @param expectedStatus 期望当前状态
     * @param targetStatus 目标状态
     * @param failureReason 失败原因（非失败状态可传 null）
     * @param updatedAt 更新时间
     * @return 更新是否成功
     */
    boolean compareAndSetStatus(
            DocumentId documentId,
            UploadStatus expectedStatus,
            UploadStatus targetStatus,
            String failureReason,
            Instant updatedAt);
}
