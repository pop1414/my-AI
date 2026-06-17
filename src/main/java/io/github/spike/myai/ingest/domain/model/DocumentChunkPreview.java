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
 * @param chunkMetadata 分块结构化元数据
 * @author spike
 * @since 1.0.0
 */
public record DocumentChunkPreview(
        int chunkIndex,
        String content,
        int contentLength,
        String sourceFile,
        String contentHash,
        String splitVersion,
        ChunkMetadata chunkMetadata) {

    /**
     * 紧凑构造函数：chunkMetadata 为 null 时归一化为默认值。
     */
    public DocumentChunkPreview {
        if (chunkMetadata == null) {
            chunkMetadata = ChunkMetadata.of(null, 0, null);
        }
    }
}
