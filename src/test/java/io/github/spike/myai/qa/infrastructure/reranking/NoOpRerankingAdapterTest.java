package io.github.spike.myai.qa.infrastructure.reranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NoOpRerankingAdapter 单元测试。
 */
class NoOpRerankingAdapterTest {

    private final NoOpRerankingAdapter adapter = new NoOpRerankingAdapter();

    @Test
    @DisplayName("rerank 应在候选数不超过 topN 时返回全部候选")
    void rerank_shouldReturnAllCandidates_whenCandidatesFitWithinTopN() {
        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk("doc-1", "kb-1", 0, "content-a"),
                new RetrievedChunk("doc-2", "kb-1", 1, "content-b"));

        List<RetrievedChunk> result = adapter.rerank(candidates, "question", 5);

        assertEquals(2, result.size());
        assertEquals("doc-1", result.get(0).documentId());
        assertEquals("doc-2", result.get(1).documentId());
    }

    @Test
    @DisplayName("rerank 应在候选数超过 topN 时截断到前 topN 条")
    void rerank_shouldTruncateToTopN_whenCandidatesExceedTopN() {
        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk("doc-1", "kb-1", 0, "a"),
                new RetrievedChunk("doc-2", "kb-1", 1, "b"),
                new RetrievedChunk("doc-3", "kb-1", 2, "c"));

        List<RetrievedChunk> result = adapter.rerank(candidates, "question", 2);

        assertEquals(2, result.size());
        assertEquals("doc-1", result.get(0).documentId());
        assertEquals("doc-2", result.get(1).documentId());
    }

    @Test
    @DisplayName("rerank 应在输入为 null 时返回空列表")
    void rerank_shouldReturnEmptyList_whenInputIsNull() {
        List<RetrievedChunk> result = adapter.rerank(null, "question", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("rerank 应在输入为空列表时返回空列表")
    void rerank_shouldReturnEmptyList_whenInputIsEmpty() {
        List<RetrievedChunk> result = adapter.rerank(List.of(), "question", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("rerank 应保持输入顺序不变（透传行为）")
    void rerank_shouldPreserveInputOrder() {
        List<RetrievedChunk> candidates = List.of(
                new RetrievedChunk("doc-3", "kb-1", 0, "c"),
                new RetrievedChunk("doc-1", "kb-1", 1, "a"),
                new RetrievedChunk("doc-2", "kb-1", 2, "b"));

        List<RetrievedChunk> result = adapter.rerank(candidates, "question", 10);

        assertEquals(3, result.size());
        assertEquals("doc-3", result.get(0).documentId());
        assertEquals("doc-1", result.get(1).documentId());
        assertEquals("doc-2", result.get(2).documentId());
    }
}
