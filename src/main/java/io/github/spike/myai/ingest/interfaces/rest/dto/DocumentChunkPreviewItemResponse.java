package io.github.spike.myai.ingest.interfaces.rest.dto;

/**
 * 分块预览项响应 DTO。
 *
 * @param chunkIndex 分块序号
 * @param contentPreview 分块预览内容
 * @param sourceFile 源文件名
 * @param contentHash 分块哈希
 * @param splitVersion 分块版本
 */
public record DocumentChunkPreviewItemResponse(
        int chunkIndex,
        String contentPreview,
        String sourceFile,
        String contentHash,
        String splitVersion) {
}

