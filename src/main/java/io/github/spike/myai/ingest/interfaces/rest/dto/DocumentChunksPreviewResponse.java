package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.util.List;

/**
 * 文档分块预览响应 DTO。
 *
 * @param documentId 文档资产 ID
 * @param chunkCount 本次返回分块数
 * @param totalChunks 文档总分块数
 * @param limit 本次返回上限
 * @param offset 起始偏移
 * @param previewChars 预览字符数
 * @param chunks 分块预览列表
 */
public record DocumentChunksPreviewResponse(
        String documentId,
        int chunkCount,
        int totalChunks,
        int limit,
        int offset,
        int previewChars,
        List<DocumentChunkPreviewItemResponse> chunks) {
}
