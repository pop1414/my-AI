package io.github.spike.myai.ingest.interfaces.rest.dto;

/**
 * 文档状态查询响应 DTO（REST Output Model）。
 *
 * @param documentId 文档 ID
 * @param status 当前状态
 */
public record DocumentStatusResponse(String documentId, String status) {
}
