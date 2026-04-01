package io.github.spike.myai.ingest.interfaces.rest.dto;

/**
 * 上传接口响应 DTO（REST Output Model）。
 *
 * <p>字段说明：
 * <ul>
 *     <li>documentId：文档资产 ID（同一 kbId + fileHash 下保持稳定）。</li>
 *     <li>status：当前处理状态（例如 ACCEPTED）。</li>
 * </ul>
 *
 * @param documentId 文档资产 ID
 * @param status 当前状态
 */
public record UploadResponse(String documentId, String status) {
}
