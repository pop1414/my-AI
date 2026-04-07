package io.github.spike.myai.ingest.domain.model;

/**
 * 文档分块预览模型。
 *
 * @param chunkIndex 分块序号
 * @param content 分块全文
 * @param sourceFile 源文件名
 * @param contentHash 分块内容哈希
 * @param splitVersion 分块版本
 */
public record DocumentChunkPreview(
        int chunkIndex,
        String content,
        String sourceFile,
        String contentHash,
        String splitVersion) {
}

