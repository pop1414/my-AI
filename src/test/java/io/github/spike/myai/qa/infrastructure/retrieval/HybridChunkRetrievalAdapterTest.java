package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.RetrievalConfigPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * HybridChunkRetrievalAdapter 单元测试。
 *
 * <p>验证 RRF 融合算法、并行执行降级策略、空查询防御等场景。</p>
 */
class HybridChunkRetrievalAdapterTest {

    private PgVectorChunkRetrievalAdapter denseAdapter;
    private SparseRetrievalAdapter sparseAdapter;
    private RetrievalConfigPort retrievalConfig;
    private HybridChunkRetrievalAdapter adapter;

    /** 测试用默认 RRF k 值 */
    private static final int DEFAULT_RRF_K = 60;
    /** 测试用默认 Dense 权重 */
    private static final double DEFAULT_DENSE_WEIGHT = 0.7;
    /** 测试用默认 Sparse 权重 */
    private static final double DEFAULT_SPARSE_WEIGHT = 0.3;

    @BeforeEach
    void setUp() {
        denseAdapter = Mockito.mock(PgVectorChunkRetrievalAdapter.class);
        sparseAdapter = Mockito.mock(SparseRetrievalAdapter.class);
        retrievalConfig = Mockito.mock(RetrievalConfigPort.class);
        when(retrievalConfig.getRrfK()).thenReturn(DEFAULT_RRF_K);
        when(retrievalConfig.getDenseWeight()).thenReturn(DEFAULT_DENSE_WEIGHT);
        when(retrievalConfig.getSparseWeight()).thenReturn(DEFAULT_SPARSE_WEIGHT);
        // 同步执行器：测试中直接在调用线程执行，确保确定性
        adapter = new HybridChunkRetrievalAdapter(denseAdapter, sparseAdapter, retrievalConfig, Runnable::run);
    }

    @Test
    @DisplayName("双路命中时 RRF 分数应正确叠加 — 验证公式 weight/(k+rank)")
    void similaritySearch_shouldFuseRrfScores_whenBothPathsReturnResults() {
        // Dense 返回 [chunkA(rank=1), chunkB(rank=2)]
        RetrievedChunk chunkA = new RetrievedChunk("doc-a", "kb-1", 0, "内容A", null, null, null, 0.9);
        RetrievedChunk chunkB = new RetrievedChunk("doc-b", "kb-1", 0, "内容B", null, null, null, 0.7);
        // Sparse 返回 [chunkB(rank=1), chunkA(rank=2)]
        RetrievedChunk chunkBFromSparse = new RetrievedChunk("doc-b", "kb-1", 0, "内容B", null, null, null, 2.1);
        RetrievedChunk chunkAFromSparse = new RetrievedChunk("doc-a", "kb-1", 0, "内容A", null, null, null, 1.5);

        when(denseAdapter.similaritySearch(eq("测试"), eq(10), any()))
                .thenReturn(List.of(chunkA, chunkB));
        when(sparseAdapter.similaritySearch(eq("测试"), eq(10), any()))
                .thenReturn(List.of(chunkBFromSparse, chunkAFromSparse));

        List<RetrievedChunk> results = adapter.similaritySearch("测试", 10, List.of());

        assertEquals(2, results.size());

        // chunkA: 0.7/(60+1) + 0.3/(60+2) = 0.011475 + 0.004839 = 0.016314
        // chunkB: 0.7/(60+2) + 0.3/(60+1) = 0.011290 + 0.004918 = 0.016208
        double expectedA = 0.7 / (60 + 1) + 0.3 / (60 + 2);
        double expectedB = 0.7 / (60 + 2) + 0.3 / (60 + 1);
        // chunkA 和 chunkB 分数不相等（权重不对称时排名影响大小不同）
        assertTrue(expectedA > expectedB, "Dense 权重更高时，Dense rank=1 的 chunkA 应得分更高");
        assertEquals(expectedA, results.get(0).score(), 1e-10, "RRF 分数应精确匹配公式");
        assertEquals(expectedB, results.get(1).score(), 1e-10, "RRF 分数应精确匹配公式");
    }

    @Test
    @DisplayName("仅 Dense 命中时应返回 Dense-only 结果，score 保留原值")
    void similaritySearch_shouldReturnDenseOnly_whenSparseReturnsEmpty() {
        RetrievedChunk chunk = new RetrievedChunk("doc-1", "kb-1", 0, "内容", null, null, null, 0.8);

        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of(chunk));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertEquals(1, results.size());
        assertEquals("doc-1", results.get(0).documentId());
        // 仅 Dense rank=1: 0.7/(60+1) = 0.011475
        assertEquals(0.7 / (60 + 1), results.get(0).score(), 1e-10);
    }

    @Test
    @DisplayName("仅 Sparse 命中时应返回 Sparse-only 结果，score 保留原值")
    void similaritySearch_shouldReturnSparseOnly_whenDenseReturnsEmpty() {
        RetrievedChunk chunk = new RetrievedChunk("doc-1", "kb-1", 0, "内容", null, null, null, 1.5);

        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of(chunk));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertEquals(1, results.size());
        assertEquals("doc-1", results.get(0).documentId());
        // 仅 Sparse rank=1: 0.3/(60+1) = 0.004918
        assertEquals(0.3 / (60 + 1), results.get(0).score(), 1e-10);
    }

    @Test
    @DisplayName("两路都返回空时结果应为空列表")
    void similaritySearch_shouldReturnEmpty_whenBothPathsReturnEmpty() {
        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Dense 失败时应降级到 Sparse-only 结果")
    void similaritySearch_shouldDegradeToSparse_whenDenseFails() {
        RetrievedChunk chunk = new RetrievedChunk("doc-1", "kb-1", 0, "Sparse 内容", null, null, null, 2.0);

        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenThrow(new RuntimeException("Dense 检索失败"));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of(chunk));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertEquals(1, results.size());
        assertEquals("doc-1", results.get(0).documentId());
        // Sparse-only 降级 rank=1: 0.3/(60+1) = 0.004918
        assertEquals(0.3 / (60 + 1), results.get(0).score(), 1e-10);
    }

    @Test
    @DisplayName("Sparse 失败时应降级到 Dense-only 结果")
    void similaritySearch_shouldDegradeToDense_whenSparseFails() {
        RetrievedChunk chunk = new RetrievedChunk("doc-1", "kb-1", 0, "Dense 内容", null, null, null, 0.9);

        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of(chunk));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenThrow(new RuntimeException("Sparse 检索失败"));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertEquals(1, results.size());
        assertEquals("doc-1", results.get(0).documentId());
        // Dense-only 降级 rank=1: 0.7/(60+1) = 0.011475
        assertEquals(0.7 / (60 + 1), results.get(0).score(), 1e-10);
    }

    @Test
    @DisplayName("两路都失败时应返回空列表")
    void similaritySearch_shouldReturnEmpty_whenBothPathsFail() {
        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenThrow(new RuntimeException("Dense 失败"));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenThrow(new RuntimeException("Sparse 失败"));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 5, List.of());

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("空查询应快速返回空列表，不触发并行检索")
    void similaritySearch_shouldReturnEmptyWithoutRetrieval_whenQuestionIsNull() {
        List<RetrievedChunk> results = adapter.similaritySearch(null, 5, List.of());

        assertTrue(results.isEmpty());
        Mockito.verifyNoInteractions(denseAdapter, sparseAdapter);
    }

    @Test
    @DisplayName("空白查询应快速返回空列表，不触发并行检索")
    void similaritySearch_shouldReturnEmptyWithoutRetrieval_whenQuestionIsBlank() {
        List<RetrievedChunk> results = adapter.similaritySearch("   ", 5, List.of());

        assertTrue(results.isEmpty());
        Mockito.verifyNoInteractions(denseAdapter, sparseAdapter);
    }

    @Test
    @DisplayName("结果应按 RRF 分降序排列")
    void similaritySearch_shouldReturnResultsSortedByRrfScoreDesc() {
        // Dense: [doc-1(rank=1), doc-2(rank=2)]
        RetrievedChunk chunk1 = new RetrievedChunk("doc-1", "kb-1", 0, "内容1", null, null, null, 0.9);
        RetrievedChunk chunk2 = new RetrievedChunk("doc-2", "kb-1", 0, "内容2", null, null, null, 0.7);
        // Sparse: [doc-3(rank=1)] — doc-3 只在 Sparse 路径出现，但排名最高
        RetrievedChunk chunk3 = new RetrievedChunk("doc-3", "kb-1", 0, "内容3", null, null, null, 3.0);

        when(denseAdapter.similaritySearch(eq("查询"), eq(10), any()))
                .thenReturn(List.of(chunk1, chunk2));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(10), any()))
                .thenReturn(List.of(chunk3));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 10, List.of());

        assertEquals(3, results.size());
        // doc-1: 0.7/61 = 0.011475
        // doc-2: 0.7/62 = 0.011290
        // doc-3: 0.3/61 = 0.004918
        // doc-1 > doc-2 > doc-3（Dense 权重更高，Dense 路径的头部结果优势更大）
        double scoreDoc1 = results.get(0).score();
        double scoreDoc2 = results.get(results.size() - 1).score();
        assertTrue(scoreDoc1 >= scoreDoc2, "结果应按 RRF 分降序");
    }

    @Test
    @DisplayName("同一 chunk 双路命中时分数应正确叠加")
    void similaritySearch_shouldAccumulateRrfScore_whenChunkHitInBothPaths() {
        RetrievedChunk fromDense = new RetrievedChunk("doc-shared", "kb-1", 0, "共享内容", null, null, null, 0.9);
        RetrievedChunk fromSparse = new RetrievedChunk("doc-shared", "kb-1", 0, "共享内容", null, null, null, 1.5);
        RetrievedChunk denseOnly = new RetrievedChunk("doc-dense", "kb-1", 0, "仅 Dense", null, null, null, 0.7);

        // Dense: [shared(rank=1), denseOnly(rank=2)]
        when(denseAdapter.similaritySearch(eq("查询"), eq(10), any()))
                .thenReturn(List.of(fromDense, denseOnly));
        // Sparse: [shared(rank=1)]
        when(sparseAdapter.similaritySearch(eq("查询"), eq(10), any()))
                .thenReturn(List.of(fromSparse));

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 10, List.of());

        assertEquals(2, results.size());
        // shared: 0.7/61 + 0.3/61 = 1.0/61 = 0.016393...
        // denseOnly: 0.7/62 = 0.011290...
        RetrievedChunk sharedResult = results.stream()
                .filter(c -> "doc-shared".equals(c.documentId()))
                .findFirst().orElseThrow();
        RetrievedChunk denseOnlyResult = results.stream()
                .filter(c -> "doc-dense".equals(c.documentId()))
                .findFirst().orElseThrow();

        double expectedShared = 0.7 / (60 + 1) + 0.3 / (60 + 1);
        double expectedDenseOnly = 0.7 / (60 + 2);
        assertEquals(expectedShared, sharedResult.score(), 1e-10, "双路命中应叠加 RRF 分");
        assertEquals(expectedDenseOnly, denseOnlyResult.score(), 1e-10, "单路命中应只计该路 RRF 分");
        assertTrue(sharedResult.score() > denseOnlyResult.score(), "双路命中分数应大于单路");
    }

    @Test
    @DisplayName("topK 截断应正确限制返回数量")
    void similaritySearch_shouldLimitResultsToTopK() {
        RetrievedChunk c1 = new RetrievedChunk("doc-1", "kb-1", 0, "A", null, null, null, 0.9);
        RetrievedChunk c2 = new RetrievedChunk("doc-2", "kb-1", 0, "B", null, null, null, 0.8);
        RetrievedChunk c3 = new RetrievedChunk("doc-3", "kb-1", 0, "C", null, null, null, 0.7);

        when(denseAdapter.similaritySearch(eq("查询"), eq(2), any()))
                .thenReturn(List.of(c1, c2, c3));
        when(sparseAdapter.similaritySearch(eq("查询"), eq(2), any()))
                .thenReturn(List.of());

        List<RetrievedChunk> results = adapter.similaritySearch("查询", 2, List.of());

        assertEquals(2, results.size(), "应截取前 topK 条");
    }

    @Test
    @DisplayName("scope 参数应正确传递给两路 adapter")
    void similaritySearch_shouldPassScopeToBothAdapters() {
        AskableDocumentVersion scope = new AskableDocumentVersion(
                "doc-1", 1, 1, "f.pdf", java.time.Instant.parse("2026-05-08T10:00:00Z"));
        List<AskableDocumentVersion> scopeList = List.of(scope);

        when(denseAdapter.similaritySearch(eq("查询"), eq(5), eq(scopeList)))
                .thenReturn(List.of());
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), eq(scopeList)))
                .thenReturn(List.of());

        adapter.similaritySearch("查询", 5, scopeList);

        Mockito.verify(denseAdapter).similaritySearch("查询", 5, scopeList);
        Mockito.verify(sparseAdapter).similaritySearch("查询", 5, scopeList);
    }

    @Test
    @DisplayName("topK 为 0 时应使用下限保护 effectiveTopK=1")
    void similaritySearch_shouldUseMinTopK_whenTopKIsZero() {
        when(denseAdapter.similaritySearch(eq("查询"), eq(1), any()))
                .thenReturn(List.of());
        when(sparseAdapter.similaritySearch(eq("查询"), eq(1), any()))
                .thenReturn(List.of());

        adapter.similaritySearch("查询", 0, List.of());

        Mockito.verify(denseAdapter).similaritySearch("查询", 1, List.of());
        Mockito.verify(sparseAdapter).similaritySearch("查询", 1, List.of());
    }

    @Test
    @DisplayName("无 scope 重载应正确委托到带 scope 方法（scope=null）")
    void similaritySearch_shouldDelegateToScopedOverload_whenNoScope() {
        // similaritySearch(question, topK) 委托到 similaritySearch(question, topK, null)
        when(denseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());
        when(sparseAdapter.similaritySearch(eq("查询"), eq(5), any()))
                .thenReturn(List.of());

        adapter.similaritySearch("查询", 5);

        Mockito.verify(denseAdapter).similaritySearch("查询", 5, null);
        Mockito.verify(sparseAdapter).similaritySearch("查询", 5, null);
    }
}
