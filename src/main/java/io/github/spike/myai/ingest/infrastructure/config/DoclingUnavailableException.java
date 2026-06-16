package io.github.spike.myai.ingest.infrastructure.config;

/**
 * Docling Serve 启动校验异常。
 *
 * <p>当应用启动时 Docling Serve 不可达或健康检查失败时抛出，
 * 触发 fail-fast 使应用启动失败并输出明确的错误消息。
 *
 * @author spike
 * @since 1.0.0
 * @see DoclingStartupVerifier
 * @see DoclingUnavailableFailureAnalyzer
 */
public class DoclingUnavailableException extends IllegalStateException {

    public DoclingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DoclingUnavailableException(String message) {
        super(message);
    }
}
