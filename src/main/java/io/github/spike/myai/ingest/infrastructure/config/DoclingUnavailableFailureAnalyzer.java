package io.github.spike.myai.ingest.infrastructure.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Docling Serve 不可用时的启动失败分析器。
 *
 * <p>捕获 {@link DoclingUnavailableException}，输出友好的错误消息和排查建议，
 * 帮助运维者快速定位 Docling Serve 连接故障。
 *
 * @author spike
 * @since 1.0.0
 * @see DoclingUnavailableException
 * @see DoclingStartupVerifier
 */
public class DoclingUnavailableFailureAnalyzer extends AbstractFailureAnalyzer<DoclingUnavailableException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, DoclingUnavailableException cause) {
        return new FailureAnalysis(
                "Docling Serve 不可用 — %s".formatted(cause.getMessage()),
                """
                        请检查以下配置:
                        1. docling-serve 容器是否已启动（cd infra && docker compose up -d docling-serve）
                        2. arconia.docling.base-url 配置是否正确（当前检查: Docling Serve API 端点）
                        3. 端口是否可达（默认 5001）
                        4. 首次启动时 Docling 需要下载模型，请等待容器 healthy 后再启动应用""",
                cause);
    }
}
