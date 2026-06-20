package io.github.spike.myai.ingest.interfaces.rest.dto;

import io.github.spike.myai.ingest.domain.model.ChunkMetadata;

/**
 * 分块预览项响应 DTO。
 *
 * @param chunkIndex 分块序号
 * @param contentLength 分块内容长度
 * @param contentPreview 分块预览内容
 * @param truncated 是否被截断
 * @param sourceFile 源文件名
 * @param contentHash 分块哈希
 * @param splitVersion 分块版本
 * @param chunkMetadata 分块结构化元数据
 */
public record DocumentChunkPreviewItemResponse(
        int chunkIndex,
        int contentLength,
        String contentPreview,
        boolean truncated,
        String sourceFile,
        String contentHash,
        String splitVersion,
        ChunkMetadata chunkMetadata) {
}
