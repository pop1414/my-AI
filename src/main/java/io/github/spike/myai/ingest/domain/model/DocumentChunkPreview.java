package io.github.spike.myai.ingest.domain.model;

/**
 * 文档分块预览模型。
 *
 * @param chunkIndex 分块序号
 * @param content 分块全文
 * @param contentLength 分块内容长度（来自原始内容，用于评估分块质量）
 * @param sourceFile 源文件名
 * @param contentHash 分块内容哈希
 * @param splitVersion 分块版本
 * @param sourceHint 来源提示（例如标题/页码，JSON 字符串）
 */
public record DocumentChunkPreview(
        int chunkIndex,
        String content,
        int contentLength,
        String sourceFile,
        String contentHash,
        String splitVersion,
        String sourceHint) {
}
