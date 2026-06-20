package io.github.spike.myai.qa.infrastructure.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.qa.infrastructure.retrieval.HybridChunkRetrievalAdapter;
import io.github.spike.myai.qa.infrastructure.retrieval.PgVectorChunkRetrievalAdapter;
import io.github.spike.myai.qa.infrastructure.retrieval.SparseRetrievalAdapter;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 检索质量评测入口（@SpringBootTest）。
 *
 * <p>三模式对比评测：Dense / Sparse / Hybrid 并行执行，
 * 生成结构化 JSON 报告到 target/eval-report-{timestamp}.json。
 * 全程不调用大模型、无外部网络请求（仅触发检索链路）。</p>
 *
 * <p>触发方式：{@code mvn test -Dtest=EvalRunnerTest}</p>
 *
 * @author spike
 * @since 1.0.0
 */
@SpringBootTest
class EvalRunnerTest {

    private static final int TOP_K = 5;

    @Autowired
    private PgVectorChunkRetrievalAdapter denseAdapter;

    @Autowired
    private SparseRetrievalAdapter sparseAdapter;

    @Autowired
    private HybridChunkRetrievalAdapter hybridAdapter;

    @Test
    @DisplayName("三模式检索质量评测 — Dense / Sparse / Hybrid 并行对比，输出 JSON 报告")
    void runRetrievalEval() throws IOException, TimeoutException {
        // 1. 加载数据集
        RetrievalEvalDatasetLoader loader = new RetrievalEvalDatasetLoader();
        List<EvalSample> dataset = loader.load("eval/retrieval-qa-pairs.json");

        RetrievalEvalExecutor executor = new RetrievalEvalExecutor();
        EvalReportGenerator reportGenerator = new EvalReportGenerator();

        // 2. 三模式并行执行（Java 21 虚拟线程，AD-12）
        List<EvalResult> denseResults;
        List<EvalResult> sparseResults;
        List<EvalResult> hybridResults;

        try (var virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<EvalResult>> denseFuture = virtualExecutor.submit(
                    () -> executor.executeSingleMode(denseAdapter, dataset, TOP_K));
            Future<List<EvalResult>> sparseFuture = virtualExecutor.submit(
                    () -> executor.executeSingleMode(sparseAdapter, dataset, TOP_K));
            Future<List<EvalResult>> hybridFuture = virtualExecutor.submit(
                    () -> executor.executeSingleMode(hybridAdapter, dataset, TOP_K));

            try {
                denseResults = denseFuture.get(60, TimeUnit.SECONDS);
                sparseResults = sparseFuture.get(60, TimeUnit.SECONDS);
                hybridResults = hybridFuture.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("评测被中断", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("三模式评测执行失败", e.getCause());
            }
        }

        // 3. 生成对比报告
        String reportJson = reportGenerator.generateComparisonJson(
                denseResults, sparseResults, hybridResults);

        // 4. 输出报告到 target/
        Path reportPath = Path.of("target", "eval-report-" + Instant.now().getEpochSecond() + ".json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, reportJson);

        // 5. 基本断言 — 报告结构完整
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(reportJson);

        assertThat(root.has("summary")).as("报告缺少 summary 层").isTrue();
        assertThat(root.get("summary").has("modes")).as("报告缺少 modes 对比").isTrue();
        assertThat(root.get("summary").get("modes").has("dense")).as("报告缺少 dense 模式汇总").isTrue();
        assertThat(root.get("summary").get("modes").has("sparse")).as("报告缺少 sparse 模式汇总").isTrue();
        assertThat(root.get("summary").get("modes").has("hybrid")).as("报告缺少 hybrid 模式汇总").isTrue();
        assertThat(root.has("by_query_type")).as("报告缺少 by_query_type 分类统计层").isTrue();
        assertThat(root.has("details")).as("报告缺少 details 单条详情层").isTrue();
        assertThat(root.get("details").size()).as("详情条数应与数据集一致").isEqualTo(dataset.size());

        // 6. 性能阈值断言 — 仅度量检索耗时（排除 Spring 启动），2x 裕度防 flaky
        long totalRetrievalMs = denseResults.stream().mapToLong(EvalResult::latencyMs).sum()
                + sparseResults.stream().mapToLong(EvalResult::latencyMs).sum()
                + hybridResults.stream().mapToLong(EvalResult::latencyMs).sum();
        assertThat(totalRetrievalMs).as("三模式检索总耗时应在 30s 内（15s × 2 buffer）").isLessThan(30_000);
    }
}
