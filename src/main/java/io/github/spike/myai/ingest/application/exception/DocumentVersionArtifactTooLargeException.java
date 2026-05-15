package io.github.spike.myai.ingest.application.exception;

/**
 * 文档版本处理产物超过读取上限异常。
 *
 * <p>正文读取链路使用该稳定异常区分“产物存在但过大”的业务分支，
 * 供 REST 层后续映射为 {@code CONTENT_TOO_LARGE}。
 */
public class DocumentVersionArtifactTooLargeException extends RuntimeException {

    private final long contentLength;
    private final long maxBytes;

    /**
     * 创建产物过大异常。
     *
     * @param contentLength 实际产物字节长度
     * @param maxBytes 允许读取的最大字节数
     */
    public DocumentVersionArtifactTooLargeException(long contentLength, long maxBytes) {
        super("document version artifact is too large");
        this.contentLength = contentLength;
        this.maxBytes = maxBytes;
    }

    /**
     * 返回实际产物字节长度。
     *
     * @return 实际字节长度
     */
    public long contentLength() {
        return contentLength;
    }

    /**
     * 返回允许读取的最大字节数。
     *
     * @return 最大字节数
     */
    public long maxBytes() {
        return maxBytes;
    }
}
