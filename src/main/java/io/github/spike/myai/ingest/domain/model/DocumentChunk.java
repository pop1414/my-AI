package io.github.spike.myai.ingest.domain.model;

/**
 * 文档分块结果模型。
 *
 * <p>每条分块携带正文内容和结构化元数据（{@link ChunkMetadata}），
 * 供下游向量索引和检索使用。
 *
 * @param content      分块正文
 * @param chunkMetadata 分块结构化元数据（headings / pageNumber / contentType）
 * @author spike
 * @since 1.0.0
 */
public record DocumentChunk(String content, ChunkMetadata chunkMetadata) {

    /**
     * 紧凑构造函数：校验 content 非空，chunkMetadata 为 null 时归一化为默认值。
     */
    public DocumentChunk {
        // content 必填，用于向量化与预览。
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (chunkMetadata == null) {
            chunkMetadata = ChunkMetadata.of(null, 0, null);
        }
    }
}
