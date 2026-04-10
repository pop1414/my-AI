package io.github.spike.myai.ingest.domain.model;

/**
 * 文档分块结果模型。
 *
 * @param content 分块正文
 * @param sourceHint 来源提示（建议 JSON 字符串，例如 {"heading":"xxx"}）
 */
public record DocumentChunk(String content, String sourceHint) {
    public DocumentChunk {
        // content 必填，用于向量化与预览。
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
    }
}
