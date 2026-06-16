package io.github.spike.myai.ingest.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * DoclingUnavailableFailureAnalyzer 的纯单元测试。
 *
 * @author spike
 * @since 1.0.0
 */
class DoclingUnavailableFailureAnalyzerTest {

    private final DoclingUnavailableFailureAnalyzer analyzer = new DoclingUnavailableFailureAnalyzer();

    @Test
    @DisplayName("故障分析 — 连接失败异常映射为包含排查建议的 FailureAnalysis")
    void analyze_shouldReturnFailureAnalysisWithAction_whenConnectionFailed() {
        // given
        DoclingUnavailableException exception = new DoclingUnavailableException(
                "Docling Serve 不可用 — 无法连接到 Docling Serve API",
                new RuntimeException("Connection refused"));

        // when
        FailureAnalysis analysis = analyzer.analyze(exception);

        // then
        assertNotNull(analysis);
        assertTrue(analysis.getDescription().contains("Docling Serve 不可用"));
        assertTrue(analysis.getAction().contains("docling-serve"));
        assertTrue(analysis.getAction().contains("docker compose up"));
        assertTrue(analysis.getAction().contains("arconia.docling.base-url"));
        assertEquals(exception, analysis.getCause());
    }

    @Test
    @DisplayName("故障分析 — 非 ok 状态异常映射为包含排查建议的 FailureAnalysis")
    void analyze_shouldReturnFailureAnalysisWithAction_whenUnhealthyStatus() {
        // given
        DoclingUnavailableException exception = new DoclingUnavailableException(
                "Docling Serve 健康检查返回非 ok 状态: error");

        // when
        FailureAnalysis analysis = analyzer.analyze(exception);

        // then
        assertNotNull(analysis);
        assertTrue(analysis.getDescription().contains("非 ok 状态"));
        assertTrue(analysis.getAction().contains("端口是否可达"));
    }
}
