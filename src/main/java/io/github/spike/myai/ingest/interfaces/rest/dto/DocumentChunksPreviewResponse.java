package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.util.List;

/**
 * 文档分块预览响应 DTO。
 *
 * @param documentId 文档资产 ID
 * @param chunkCount 本次返回分块数
 * @param chunks 分块预览列表
 */
public record DocumentChunksPreviewResponse(
        String documentId,
        int chunkCount,
        List<DocumentChunkPreviewItemResponse> chunks) {
}

