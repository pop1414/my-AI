package io.github.spike.myai.ingest.domain.model;

/**
 * Docling Serve 解析异常基类。
 *
 * <p>所有 Docling 解析相关的异常均继承此类，
 * 用于在 {@code DoclingDocumentParser} 和 Worker 重试策略之间建立明确的错误分类契约。
 *
 * <p>异常层次：
 * <ul>
 *   <li>{@link DoclingPermanentException} — 4xx 客户端错误，不可重试</li>
 *   <li>{@link DoclingTransientException} — 5xx/超时/网络错误，可重试</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public class DoclingParseException extends RuntimeException {

    /**
     * 构造 Docling 解析异常。
     *
     * @param message 错误描述
     * @param cause   原始异常（保留异常链）
     */
    public DoclingParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造 Docling 解析异常（无 cause）。
     *
     * @param message 错误描述
     */
    public DoclingParseException(String message) {
        super(message);
    }
}
