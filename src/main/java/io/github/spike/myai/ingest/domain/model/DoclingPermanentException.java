package io.github.spike.myai.ingest.domain.model;

/**
 * Docling Serve 永久性解析异常（4xx 客户端错误）。
 *
 * <p>当 Docling Serve 返回 4xx HTTP 状态码时抛出，
 * 表示请求本身存在问题（不支持的格式、参数错误等），重试不会恢复。
 * Worker 层将此类异常映射为 {@code markFailed}（文档进入 FAILED 状态）。
 *
 * @author spike
 * @since 1.0.0
 */
public class DoclingPermanentException extends DoclingParseException {

    private final int httpStatusCode;

    /**
     * 构造 Docling 永久性异常。
     *
     * @param message        错误描述
     * @param httpStatusCode HTTP 状态码（4xx）
     * @param cause          原始异常（保留异常链）
     */
    public DoclingPermanentException(String message, int httpStatusCode, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
    }

    /**
     * 返回触发此异常的 HTTP 状态码。
     *
     * @return HTTP 状态码（如 400、422）
     */
    public int httpStatusCode() {
        return httpStatusCode;
    }
}
