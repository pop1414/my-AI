package io.github.spike.myai.ingest.application.result;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;

/**
 * 查询文档状态用例的返回结果（Application Result）。
 *
 * @param documentId 文档资产 ID
 * @param latestVersionNumber 当前最新版本号
 * @param latestFilename 当前最新版本来源文件名
 * @param latestVersionOriginType 当前最新版本来源类型
 * @param status 当前处理状态
 * @param processingMetadata 仅在终态允许暴露的处理结果元数据 JSON 字符串
 */
public record DocumentStatusResult(
        DocumentId documentId,
        int latestVersionNumber,
        String latestFilename,
        DocumentVersionOriginType latestVersionOriginType,
        UploadStatus status,
        String processingMetadata) {
}
