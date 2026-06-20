package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于 RRF（Reciprocal Rank Fusion）的混合检索适配器。
 *
 * <p>该类是问答检索能力的基础设施实现（Adapter），同时编排 Dense 和 Sparse
 * 两路检索，通过 RRF 融合算法合并结果，对调用方完全透明（AD-2）。</p>
 *
 * <p>职责：
 * <ul>
 *   <li>并行调用 {@link PgVectorChunkRetrievalAdapter}（Dense）和 {@link SparseRetrievalAdapter}（Sparse）；</li>
 *   <li>对单路失败做降级处理（日志 WARN，不抛异常）；</li>
 *   <li>通过 RRF 融合算法合并两路结果，同一 chunk 双路命中时分数叠加；</li>
 *   <li>按 RRF 分降序返回前 topK 条结果。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>标注 {@code @Primary}，替代 {@link PgVectorChunkRetrievalAdapter} 作为默认 {@link ChunkRetrievalPort} 实现；</li>
 *   <li>Dense/Sparse 并行执行，总延迟 ≈ max(Dense, Sparse) + RRF(&lt;1ms)，满足 NFR-1 ≤200ms 增量约束；</li>
 *   <li>RRF 常量（k、权重）作为类内 private static final，不放配置文件（AD-9）。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Primary
@Component
public class HybridChunkRetrievalAdapter implements ChunkRetrievalPort {

    /** 当前适配器使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(HybridChunkRetrievalAdapter.class);

    /** RRF 平滑常数，标准值 60（Cormack et al., 2009）。 */
    private static final int RRF_K = 60;

    /** Dense 路径权重（等权重）。 */
    private static final double DENSE_WEIGHT = 0.5;

    /** Sparse 路径权重（等权重）。 */
    private static final double SPARSE_WEIGHT = 0.5;

    /** Dense 检索适配器（PGVector 向量相似度）。 */
    private final PgVectorChunkRetrievalAdapter denseAdapter;

    /** Sparse 检索适配器（BM25 全文检索）。 */
    private final SparseRetrievalAdapter sparseAdapter;

    /** 异步执行器：用于并行调度 Dense/Sparse 两路阻塞 JDBC 调用，避免 ForkJoinPool 线程饥饿。 */
    private final Executor executor;

    /**
     * 构造混合检索适配器。
     *
     * @param denseAdapter Dense 检索适配器，由 Spring 容器注入
     * @param sparseAdapter Sparse 检索适配器，由 Spring 容器注入
     * @param executor 异步执行器，推荐使用虚拟线程（{@code Executors.newVirtualThreadPerTaskExecutor()}）
     */
    public HybridChunkRetrievalAdapter(
            PgVectorChunkRetrievalAdapter denseAdapter,
            SparseRetrievalAdapter sparseAdapter,
            Executor executor) {
        this.denseAdapter = denseAdapter;
        this.sparseAdapter = sparseAdapter;
        this.executor = executor;
    }

    /**
     * 执行混合检索。
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数
     * @return RRF 融合后的分块列表，按融合分降序
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK) {
        return similaritySearch(question, topK, null);
    }

    /**
     * 执行带可问答版本范围的混合检索。
     *
     * <p>内部编排：
     * <ol>
     *   <li>并行调用 Dense 和 Sparse 两路检索；</li>
     *   <li>对单路失败做降级处理（返回空 list，日志 WARN）；</li>
     *   <li>RRF 融合两路结果；</li>
     *   <li>按融合分降序截取 topK 条返回。</li>
     * </ol>
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数
     * @param scope 可问答文档版本范围；为空时两路均不附加版本过滤
     * @return RRF 融合后的分块列表，按融合分降序
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        int effectiveTopK = Math.max(1, topK);

        CompletableFuture<List<RetrievedChunk>> denseFuture = CompletableFuture
                .supplyAsync(() -> denseAdapter.similaritySearch(question, effectiveTopK, scope), executor)
                .exceptionally(ex -> {
                    log.warn("Dense retrieval failed, degrading to sparse-only", ex);
                    return List.of();
                });

        CompletableFuture<List<RetrievedChunk>> sparseFuture = CompletableFuture
                .supplyAsync(() -> sparseAdapter.similaritySearch(question, effectiveTopK, scope), executor)
                .exceptionally(ex -> {
                    log.warn("Sparse retrieval failed, degrading to dense-only", ex);
                    return List.of();
                });

        CompletableFuture.allOf(denseFuture, sparseFuture).join();

        List<RetrievedChunk> denseResults = denseFuture.join();
        List<RetrievedChunk> sparseResults = sparseFuture.join();

        return fuseByRrf(denseResults, sparseResults, effectiveTopK);
    }

    /**
     * RRF 融合算法。
     *
     * <p>对两路结果按排名计算 RRF 分数并合并。同一 chunk 双路命中时分数叠加。</p>
     *
     * @param denseResults Dense 路径结果（排名顺序 = list 索引 + 1）
     * @param sparseResults Sparse 路径结果（排名顺序 = list 索引 + 1）
     * @param topK 最大返回条数
     * @return 融合后的分块列表，按 RRF 分降序
     */
    private List<RetrievedChunk> fuseByRrf(
            List<RetrievedChunk> denseResults,
            List<RetrievedChunk> sparseResults,
            int topK) {

        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RetrievedChunk> representativeChunks = new LinkedHashMap<>();

        for (int rank = 0; rank < denseResults.size(); rank++) {
            RetrievedChunk chunk = denseResults.get(rank);
            String key = chunkKey(chunk);
            rrfScores.merge(key, DENSE_WEIGHT / (RRF_K + rank + 1), Double::sum);
            representativeChunks.putIfAbsent(key, chunk);
        }

        for (int rank = 0; rank < sparseResults.size(); rank++) {
            RetrievedChunk chunk = sparseResults.get(rank);
            String key = chunkKey(chunk);
            rrfScores.merge(key, SPARSE_WEIGHT / (RRF_K + rank + 1), Double::sum);
            representativeChunks.putIfAbsent(key, chunk);
        }

        return representativeChunks.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        rrfScores.get(b.getKey()), rrfScores.get(a.getKey())))
                .limit(topK)
                .map(entry -> {
                    RetrievedChunk original = entry.getValue();
                    double rrfScore = rrfScores.get(entry.getKey());
                    return new RetrievedChunk(
                            original.documentId(),
                            original.kbId(),
                            original.chunkIndex(),
                            original.content(),
                            original.sourceVersionNumber(),
                            original.sourceFilename(),
                            original.sourceUpdatedAt(),
                            rrfScore);
                })
                .toList();
    }

    /**
     * 生成 chunk 的唯一标识 key，用于双路结果匹配。
     *
     * @param chunk 检索结果分块
     * @return composite key（documentId#chunkIndex）
     */
    private static String chunkKey(RetrievedChunk chunk) {
        return chunk.documentId() + "#" + chunk.chunkIndex();
    }
}
