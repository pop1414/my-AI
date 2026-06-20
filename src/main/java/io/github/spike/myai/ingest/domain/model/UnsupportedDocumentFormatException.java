package io.github.spike.myai.ingest.domain.model;

/**
 * 不支持的文档格式异常。
 *
 * <p>当上传的文件格式不在系统支持的 8 种格式范围内时抛出。
 * 属于永久性错误，不应重试。{@link io.github.spike.myai.ingest.application.service.RetryPolicy}
 * 将其归类为永久错误（{@code RetryDecision(transientError=false)}），文档直接标记为 FAILED。
 *
 * @author spike
 * @since 1.0.0
 */
public class UnsupportedDocumentFormatException extends RuntimeException {

    /** 不支持的格式名（如 "csv"、"epub"） */
    private final String format;

    /**
     * 构造不支持格式异常。
     *
     * @param format 不支持的文件格式名
     */
    public UnsupportedDocumentFormatException(String format) {
        super("不支持的文件格式: " + format);
        this.format = format;
    }

    /**
     * 获取不支持的格式名。
     *
     * @return 格式名（如 "csv"）
     */
    public String format() {
        return format;
    }
}
