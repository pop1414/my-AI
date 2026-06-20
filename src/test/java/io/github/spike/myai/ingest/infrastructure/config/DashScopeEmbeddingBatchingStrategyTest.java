package io.github.spike.myai.ingest.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;

/**
 * {@link DashScopeEmbeddingBatchingStrategy} 纯单元测试。
 *
 * @author spike
 * @since 1.0.0
 */
class DashScopeEmbeddingBatchingStrategyTest {

    @Test
    @DisplayName("批处理应在文档条数超过 10 时按 DashScope 上限拆分")
    void batch_shouldSplitByDashScopeLimit_whenDocumentCountExceedsTwenty() {
        DashScopeEmbeddingBatchingStrategy strategy = new DashScopeEmbeddingBatchingStrategy(
                DashScopeEmbeddingBatchingStrategy.DEFAULT_MAX_DOCUMENTS_PER_BATCH,
                documents -> List.of(documents));
        List<Document> documents = createDocuments(25);

        List<List<Document>> batches = strategy.batch(documents);

        assertEquals(3, batches.size());
        assertEquals(10, batches.get(0).size());
        assertEquals(10, batches.get(1).size());
        assertEquals(5, batches.get(2).size());
        assertEquals("doc-0", batches.get(0).get(0).getId());
        assertEquals("doc-9", batches.get(0).get(9).getId());
        assertEquals("doc-10", batches.get(1).get(0).getId());
        assertEquals("doc-20", batches.get(2).get(0).getId());
    }

    @Test
    @DisplayName("批处理应在应用条数上限后继续保留委托策略的细粒度拆分")
    void batch_shouldPreserveDelegateSplit_whenDelegateFurtherSplitsLimitedBatch() {
        BatchingStrategy delegate = documents -> {
            if (documents.size() <= 1) {
                return List.of(documents);
            }

            List<List<Document>> batches = new ArrayList<>();
            batches.add(new ArrayList<>(documents.subList(0, 1)));
            batches.add(new ArrayList<>(documents.subList(1, documents.size())));
            return batches;
        };
        DashScopeEmbeddingBatchingStrategy strategy = new DashScopeEmbeddingBatchingStrategy(
                DashScopeEmbeddingBatchingStrategy.DEFAULT_MAX_DOCUMENTS_PER_BATCH,
                delegate);
        List<Document> documents = createDocuments(23);

        List<List<Document>> batches = strategy.batch(documents);
        List<String> flattenedIds = batches.stream()
                .flatMap(List::stream)
                .map(Document::getId)
                .toList();

        assertEquals(List.of(1, 9, 1, 9, 1, 2), batches.stream().map(List::size).toList());
        assertIterableEquals(
                IntStream.range(0, 23).mapToObj(index -> "doc-" + index).toList(),
                flattenedIds);
    }

    /**
     * 构造测试文档列表，顺序即为预期 embedding 返回顺序。
     *
     * @param size 文档数量
     * @return 测试文档列表
     */
    private static List<Document> createDocuments(int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> Document.builder()
                        .id("doc-" + index)
                        .text("chunk-" + index)
                        .build())
                .toList();
    }
}
