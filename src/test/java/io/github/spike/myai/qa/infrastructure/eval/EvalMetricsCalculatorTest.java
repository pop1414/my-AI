package io.github.spike.myai.qa.infrastructure.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * EvalMetricsCalculator 单元测试。
 *
 * <p>覆盖场景：正常用例、全部命中、零命中、空相关文档列表、单条结果等边界场景。</p>
 *
 * @author spike
 * @since 1.0.0
 */
class EvalMetricsCalculatorTest {

    // === Recall@K ===

    @Nested
    @DisplayName("Recall@K")
    class RecallAtKTests {

        @Test
        @DisplayName("正常用例 — 2/3 命中，Recall@5 = 0.667")
        void recallAtK_shouldCalculateCorrectly_whenPartialHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c", "doc-d", "doc-e");
            Set<String> relevant = Set.of("doc-a", "doc-b", "doc-x");

            double recall = EvalMetricsCalculator.recallAtK(retrieved, relevant, 5);

            assertThat(recall).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("全部命中 — Recall@5 = 1.0")
        void recallAtK_shouldReturnOne_whenAllHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-a", "doc-b", "doc-c");

            double recall = EvalMetricsCalculator.recallAtK(retrieved, relevant, 5);

            assertThat(recall).isEqualTo(1.0);
        }

        @Test
        @DisplayName("零命中 — Recall@5 = 0.0")
        void recallAtK_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x", "doc-y");

            double recall = EvalMetricsCalculator.recallAtK(retrieved, relevant, 5);

            assertThat(recall).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — Recall@5 = 1.0（默认值）")
        void recallAtK_shouldReturnOne_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double recall = EvalMetricsCalculator.recallAtK(retrieved, Set.of(), 5);

            assertThat(recall).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K 截断 — 第 6 条命中的不算")
        void recallAtK_shouldRespectKLimit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c", "doc-d", "doc-e", "doc-f");
            Set<String> relevant = Set.of("doc-a", "doc-f"); // doc-f 在第 6 位，超出 K=5

            double recall = EvalMetricsCalculator.recallAtK(retrieved, relevant, 5);

            assertThat(recall).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    // === MRR ===

    @Nested
    @DisplayName("MRR")
    class MrrTests {

        @Test
        @DisplayName("第一个结果命中 — MRR = 1.0")
        void mrr_shouldReturnOne_whenFirstHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-a");

            double mrr = EvalMetricsCalculator.mrr(retrieved, relevant);

            assertThat(mrr).isEqualTo(1.0);
        }

        @Test
        @DisplayName("第三个结果命中 — MRR = 1/3")
        void mrr_shouldReturnReciprocalRank_whenThirdHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-c");

            double mrr = EvalMetricsCalculator.mrr(retrieved, relevant);

            assertThat(mrr).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("零命中 — MRR = 0.0")
        void mrr_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x");

            double mrr = EvalMetricsCalculator.mrr(retrieved, relevant);

            assertThat(mrr).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — MRR = 0.0")
        void mrr_shouldReturnZero_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double mrr = EvalMetricsCalculator.mrr(retrieved, Set.of());

            assertThat(mrr).isEqualTo(0.0);
        }

        @Test
        @DisplayName("单条结果命中 — MRR = 1.0")
        void mrr_shouldReturnOne_whenSingleResultHit() {
            List<String> retrieved = List.of("doc-a");
            Set<String> relevant = Set.of("doc-a");

            double mrr = EvalMetricsCalculator.mrr(retrieved, relevant);

            assertThat(mrr).isEqualTo(1.0);
        }

        @Test
        @DisplayName("检索结果为空 — MRR = 0.0")
        void mrr_shouldReturnZero_whenEmptyResults() {
            double mrr = EvalMetricsCalculator.mrr(List.of(), Set.of("doc-a"));

            assertThat(mrr).isEqualTo(0.0);
        }
    }

    // === HitRate@K ===

    @Nested
    @DisplayName("HitRate@K")
    class HitRateAtKTests {

        @Test
        @DisplayName("至少命中 1 条 — HitRate@5 = 1.0")
        void hitRateAtK_shouldReturnOne_whenAtLeastOneHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-b", "doc-x");

            double hitRate = EvalMetricsCalculator.hitRateAtK(retrieved, relevant, 5);

            assertThat(hitRate).isEqualTo(1.0);
        }

        @Test
        @DisplayName("零命中 — HitRate@5 = 0.0")
        void hitRateAtK_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x");

            double hitRate = EvalMetricsCalculator.hitRateAtK(retrieved, relevant, 5);

            assertThat(hitRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — HitRate@5 = 1.0（默认值）")
        void hitRateAtK_shouldReturnOne_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double hitRate = EvalMetricsCalculator.hitRateAtK(retrieved, Set.of(), 5);

            assertThat(hitRate).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K 截断 — 命中只在第 6 条时 HitRate@5 = 0.0")
        void hitRateAtK_shouldReturnZero_whenHitBeyondK() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c", "doc-d", "doc-e", "doc-f");
            Set<String> relevant = Set.of("doc-f"); // 第 6 位，超出 K=5

            double hitRate = EvalMetricsCalculator.hitRateAtK(retrieved, relevant, 5);

            assertThat(hitRate).isEqualTo(0.0);
        }

        @Test
        @DisplayName("检索结果为空 — HitRate@5 = 0.0")
        void hitRateAtK_shouldReturnZero_whenEmptyResults() {
            double hitRate = EvalMetricsCalculator.hitRateAtK(List.of(), Set.of("doc-a"), 5);

            assertThat(hitRate).isEqualTo(0.0);
        }
    }

    // === NDCG@K（Phase 2 预留） ===

    @Test
    @DisplayName("NDCG@K — Phase 2 预留，当前抛 UnsupportedOperationException")
    void ndcgAtK_shouldThrowUnsupportedOperationException() {
        assertThatThrownBy(() ->
                EvalMetricsCalculator.ndcgAtK(List.of("doc-a"), Map.of(), 5))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("NDCG@K: Phase 2");
    }
}
