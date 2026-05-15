package io.github.spike.myai.ingest.interfaces.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 文档状态查询响应 DTO（REST Output Model）。
 *
 * @param documentId 文档资产 ID
 * @param latestVersionNumber 当前最新版本号
 * @param latestFilename 当前最新版本来源文件名
 * @param latestVersionOriginType 当前最新版本来源类型
 * @param status 当前状态
 * @param processingMetadata 文档处理结果元数据，仅终态时可能返回
 */
public record DocumentStatusResponse(
        String documentId,
        int latestVersionNumber,
        String latestFilename,
        String latestVersionOriginType,
        String status,
        JsonNode processingMetadata) {
}
