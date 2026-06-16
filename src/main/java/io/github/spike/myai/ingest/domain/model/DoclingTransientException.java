package io.github.spike.myai.ingest.domain.model;

/**
 * Docling Serve 瞬时解析异常（5xx 服务端错误、超时、网络异常）。
 *
 * <p>当 Docling Serve 返回 5xx HTTP 状态码、连接超时或网络不可达时抛出，
 * 表示服务端暂时不可用，重试可能恢复。
 * Worker 层将此类异常映射为 {@code markRetry}（文档回到 UPLOADED 状态，指数退避重试）。
 *
 * @author spike
 * @since 1.0.0
 */
public class DoclingTransientException extends DoclingParseException {

    /**
     * 构造 Docling 瞬时异常。
     *
     * @param message 错误描述
     * @param cause   原始异常（保留异常链）
     */
    public DoclingTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
