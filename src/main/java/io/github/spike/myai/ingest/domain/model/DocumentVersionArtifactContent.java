package io.github.spike.myai.ingest.domain.model;

/**
 * 文档版本处理产物内容。
 *
 * <p>该值对象承载从版本级 artifact 存储中读取到的正文内容及其定位信息。
 * 上层正文接口只消费该对象，不感知本地文件路径或对象存储 SDK。
 *
 * @param key 存储层逻辑 key，用于审计和排障，不是本地文件路径
 * @param content 产物正文内容，当前正文读取专项使用 Markdown 文本
 * @param contentLength 正文 UTF-8 字节长度
 */
public record DocumentVersionArtifactContent(String key, String content, long contentLength) {

    /**
     * 紧凑构造器：校验 artifact 读取结果的基本不变量。
     */
    public DocumentVersionArtifactContent {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
    }
}
