package io.github.spike.myai.ingest.application.result;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.List;

/**
 * 文档分块预览结果。
 *
 * @param documentId 文档资产 ID
 * @param chunkCount 本次返回分块数
 * @param chunks 分块预览列表
 */
public record DocumentChunksPreviewResult(
        DocumentId documentId,
        int chunkCount,
        List<DocumentChunkPreviewItemResult> chunks) {
}

