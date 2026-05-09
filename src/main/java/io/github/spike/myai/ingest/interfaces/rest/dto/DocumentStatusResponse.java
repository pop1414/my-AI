package io.github.spike.myai.ingest.interfaces.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 文档状态查询响应 DTO（REST Output Model）。
 *
 * @param documentId 文档资产 ID
 * @param status 当前状态
 * @param processingMetadata 文档处理结果元数据，仅终态时可能返回
 */
public record DocumentStatusResponse(String documentId, String status, JsonNode processingMetadata) {
}
