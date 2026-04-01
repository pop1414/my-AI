package io.github.spike.myai.ingest.domain.model;

import java.time.Instant;

/**
 * 文档聚合根（Aggregate Root）。
 *
 * <p>该对象表示一条“文档入库任务”的核心业务状态，包含：
 * <ul>
 *     <li>文档资产标识、知识库、文件信息</li>
 *     <li>文件哈希（用于上传幂等判断）</li>
 *     <li>处理状态与失败原因</li>
 *     <li>创建时间与更新时间</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *     <li>把与文档状态相关的规则集中在领域层，避免散落在控制器或基础设施中。</li>
 *     <li>后续扩展状态流转（如重试、回滚）时，优先在该聚合中演进。</li>
 * </ul>
 */
public record Document(
        DocumentId documentId,
        String kbId,
        String fileHash,
        String filename,
        long fileSize,
        UploadStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 记录构造校验：保证领域对象在创建后就是“有效对象”。
     */
    public Document {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }
    }

    /**
     * 创建“已上传元数据”的新文档聚合。
     *
     * @param documentId 文档唯一标识
     * @param kbId 知识库 ID
     * @param fileHash 文件内容哈希（SHA-256）
     * @param filename 原始文件名
     * @param fileSize 文件大小（字节）
     * @param now 当前时间
     * @return 初始状态为 UPLADED 的文档聚合
     */
    public static Document uploaded(
            DocumentId documentId,
            String kbId,
            String fileHash,
            String filename,
            long fileSize,
            Instant now) {
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
        return new Document(documentId, kbId, fileHash, filename, fileSize, UploadStatus.UPLOADED, null, now, now);
    }

    /**
     * 进入“处理中”状态。
     *
     * @param at 更新时间
     * @return 新状态文档（不可变对象，返回新实例）
     */
    public Document markIngesting(Instant at) {
        return withStatus(UploadStatus.INGESTING, null, at);
    }

    /**
     * 标记“索引完成”状态。
     *
     * @param at 更新时间
     * @return 新状态文档
     */
    public Document markIndexed(Instant at) {
        return withStatus(UploadStatus.INDEXED, null, at);
    }

    /**
     * 标记“处理失败”状态。
     *
     * @param reason 失败原因，可用于排障
     * @param at 更新时间
     * @return 新状态文档
     */
    public Document markFailed(String reason, Instant at) {
        return withStatus(UploadStatus.FAILED, reason, at);
    }

    // Record是不可变对象，不能修改原有对象的字端，修改状态必须返回“新的Document实例”
    // 传入的参数就是可能会变动的参数：状态，失败原因，更新时间
    private Document withStatus(UploadStatus newStatus, String newFailureReason, Instant at) {
        return new Document(documentId, kbId, fileHash, filename, fileSize, newStatus, newFailureReason, createdAt, at);
    }
}
