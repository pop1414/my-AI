package io.github.spike.myai.ingest.domain.model;

import java.time.Instant;

/**
 * 文档版本事实。
 *
 * <p>该模型承载版本级文件事实、处理事实与版本来源事实，
 * 用于把稳定的 `document` 身份与版本细节分离。
 *
 * @param documentId 文档资产 ID
 * @param versionNumber 版本号，从 1 开始递增
 * @param versionOriginType 版本来源类型
 * @param rollbackFromVersionNumber 回退来源版本号，仅回退产生版本时有值
 * @param fileHash 文件内容哈希
 * @param filename 来源文件名
 * @param fileSize 文件大小
 * @param status 版本处理状态
 * @param failureReason 失败原因
 * @param retryCount 当前重试次数
 * @param retryMax 最大重试次数
 * @param nextRetryAt 下次重试时间
 * @param lastErrorCode 最近一次错误码
 * @param lastErrorMessage 最近一次错误消息
 * @param lastErrorAt 最近一次错误时间
 * @param reprocessCount 当前版本的重处理次数
 * @param reprocessRequestedAt 最近一次重处理请求时间
 * @param splitVersion 当前版本对应的分块版本
 * @param processingMetadata 处理结果元数据
 * @param createdAt 版本创建时间
 * @param updatedAt 版本更新时间
 */
public record DocumentVersion(
        DocumentId documentId,
        int versionNumber,
        DocumentVersionOriginType versionOriginType,
        Integer rollbackFromVersionNumber,
        String fileHash,
        String filename,
        long fileSize,
        UploadStatus status,
        String failureReason,
        int retryCount,
        int retryMax,
        Instant nextRetryAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        int reprocessCount,
        Instant reprocessRequestedAt,
        String splitVersion,
        String processingMetadata,
        Instant createdAt,
        Instant updatedAt) {

    public DocumentVersion {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
        if (versionOriginType == null) {
            throw new IllegalArgumentException("versionOriginType must not be null");
        }
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (retryMax < 1) {
            throw new IllegalArgumentException("retryMax must be positive");
        }
        if (splitVersion == null || splitVersion.isBlank()) {
            throw new IllegalArgumentException("splitVersion must not be blank");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }
    }
}
