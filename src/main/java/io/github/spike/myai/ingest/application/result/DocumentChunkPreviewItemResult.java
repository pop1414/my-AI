package io.github.spike.myai.ingest.application.result;

/**
 * 文档分块预览项。
 *
 * @param chunkIndex 分块序号
 * @param contentLength 分块内容长度
 * @param contentPreview 截断后的内容预览
 * @param truncated 是否发生截断
 * @param sourceFile 源文件名
 * @param contentHash 分块内容哈希
 * @param splitVersion 分块版本
 * @param sourceHint 来源提示
 */
public record DocumentChunkPreviewItemResult(
        int chunkIndex,
        int contentLength,
        String contentPreview,
        boolean truncated,
        String sourceFile,
        String contentHash,
        String splitVersion,
        String sourceHint) {
}
