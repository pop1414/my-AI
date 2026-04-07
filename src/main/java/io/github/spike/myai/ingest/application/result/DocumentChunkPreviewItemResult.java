package io.github.spike.myai.ingest.application.result;

/**
 * 文档分块预览项。
 *
 * @param chunkIndex 分块序号
 * @param contentPreview 截断后的内容预览
 * @param sourceFile 源文件名
 * @param contentHash 分块内容哈希
 * @param splitVersion 分块版本
 */
public record DocumentChunkPreviewItemResult(
        int chunkIndex,
        String contentPreview,
        String sourceFile,
        String contentHash,
        String splitVersion) {
}

