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

    // === NDCG@K ===

    @Nested
    @DisplayName("NDCG@K")
    class NdcgAtKTests {

        @Test
        @DisplayName("理想排序 — STRONG 文档排在最前，NDCG@5 = 1.0")
        void ndcgAtK_shouldReturnOne_whenIdealOrder() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Map<String, RelevanceLevel> levels = Map.of(
                    "doc-a", RelevanceLevel.STRONG,
                    "doc-b", RelevanceLevel.STRONG,
                    "doc-c", RelevanceLevel.WEAK);

            double ndcg = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 5);

            assertThat(ndcg).isEqualTo(1.0);
        }

        @Test
        @DisplayName("非理想排序 — WEAK 排在 STRONG 前面，NDCG@5 < 1.0")
        void ndcgAtK_shouldBeLessThanOne_whenSuboptimalOrder() {
            // 理想排序：doc-a(STRONG=2), doc-b(WEAK=1)
            // 实际排序：doc-b(WEAK=1), doc-a(STRONG=2)
            List<String> retrieved = List.of("doc-b", "doc-a");
            Map<String, RelevanceLevel> levels = Map.of(
                    "doc-a", RelevanceLevel.STRONG,
                    "doc-b", RelevanceLevel.WEAK);

            double ndcg = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 5);

            assertThat(ndcg).isLessThan(1.0).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("零命中 — 所有返回文档均无标注，NDCG@5 = 0.0")
        void ndcgAtK_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Map<String, RelevanceLevel> levels = Map.of(
                    "doc-x", RelevanceLevel.STRONG);

            double ndcg = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 5);

            assertThat(ndcg).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — NDCG@5 = 1.0（CHITCHAT 兜底）")
        void ndcgAtK_shouldReturnOne_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double ndcg = EvalMetricsCalculator.ndcgAtK(retrieved, Map.of(), 5);

            assertThat(ndcg).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K 截断 — 排在第 4 位的 STRONG 文档不影响 NDCG@3")
        void ndcgAtK_shouldRespectKLimit() {
            // top-3: doc-x(无标注), doc-x(无标注), doc-x(无标注)
            // 第4位才是 doc-a(STRONG)
            List<String> retrieved = List.of("doc-x1", "doc-x2", "doc-x3", "doc-a");
            Map<String, RelevanceLevel> levels = Map.of("doc-a", RelevanceLevel.STRONG);

            double ndcg3 = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 3);
            double ndcg5 = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 5);

            assertThat(ndcg3).isEqualTo(0.0);
            assertThat(ndcg5).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("检索结果为空 — NDCG@5 = 0.0")
        void ndcgAtK_shouldReturnZero_whenEmptyResults() {
            Map<String, RelevanceLevel> levels = Map.of("doc-a", RelevanceLevel.STRONG);

            double ndcg = EvalMetricsCalculator.ndcgAtK(List.of(), levels, 5);

            assertThat(ndcg).isEqualTo(0.0);
        }

        @Test
        @DisplayName("混合相关度 — STRONG + WEAK 组合验证")
        void ndcgAtK_shouldHandleMixedRelevanceLevels() {
            // 理想排序：doc-a(S=2), doc-b(S=2), doc-c(W=1)
            // 实际排序：doc-c(W=1), doc-a(S=2), doc-b(S=2)
            // DCG  = 1/log2(2) + 2/log2(3) + 2/log2(4) = 1.0 + 1.262 + 1.0 = 3.262
            // IDCG = 2/log2(2) + 2/log2(3) + 1/log2(4) = 2.0 + 1.262 + 0.5 = 3.762
            // NDCG = 3.262 / 3.762 ≈ 0.867
            List<String> retrieved = List.of("doc-c", "doc-a", "doc-b");
            Map<String, RelevanceLevel> levels = Map.of(
                    "doc-a", RelevanceLevel.STRONG,
                    "doc-b", RelevanceLevel.STRONG,
                    "doc-c", RelevanceLevel.WEAK);

            double ndcg = EvalMetricsCalculator.ndcgAtK(retrieved, levels, 5);

            assertThat(ndcg).isCloseTo(3.262 / 3.762, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("k < 0 — 抛出 IllegalArgumentException")
        void ndcgAtK_shouldThrow_whenNegativeK() {
            assertThatThrownBy(() ->
                    EvalMetricsCalculator.ndcgAtK(List.of("doc-a"), Map.of(), -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // === MAP@K ===

    @Nested
    @DisplayName("MAP@K")
    class MapAtKTests {

        @Test
        @DisplayName("正常用例 — 第1、3位命中2个strong文档")
        void mapAtK_shouldCalculateCorrectly_whenPartialHit() {
            // 位置1命中: Precision@1 = 1/1 = 1.0
            // 位置3命中: Precision@3 = 2/3 ≈ 0.667
            // MAP = (1.0 + 0.667) / 2（strong总数）= 0.833
            List<String> retrieved = List.of("doc-a", "doc-x", "doc-b", "doc-y", "doc-z");
            Set<String> relevant = Set.of("doc-a", "doc-b");

            double map = EvalMetricsCalculator.mapAtK(retrieved, relevant, 5);

            assertThat(map).isCloseTo((1.0 + 2.0 / 3.0) / 2.0, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("全部命中且排在最前 — MAP@5 = 1.0")
        void mapAtK_shouldReturnOne_whenAllHitAtTop() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-a", "doc-b", "doc-c");

            double map = EvalMetricsCalculator.mapAtK(retrieved, relevant, 5);

            assertThat(map).isEqualTo(1.0);
        }

        @Test
        @DisplayName("零命中 — MAP@5 = 0.0")
        void mapAtK_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x", "doc-y");

            double map = EvalMetricsCalculator.mapAtK(retrieved, relevant, 5);

            assertThat(map).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — MAP@5 = 1.0（默认值）")
        void mapAtK_shouldReturnOne_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double map = EvalMetricsCalculator.mapAtK(retrieved, Set.of(), 5);

            assertThat(map).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K 截断 — 第 6 位命中的不算入 MAP@5")
        void mapAtK_shouldRespectKLimit() {
            List<String> retrieved = List.of("doc-a", "doc-x", "doc-x", "doc-x", "doc-x", "doc-b");
            Set<String> relevant = Set.of("doc-a", "doc-b");

            double map5 = EvalMetricsCalculator.mapAtK(retrieved, relevant, 5);
            double map10 = EvalMetricsCalculator.mapAtK(retrieved, relevant, 10);

            // MAP@5: 只有 doc-a 命中，(1/1) / 2 = 0.5
            assertThat(map5).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
            // MAP@10: doc-a@1 + doc-b@6，(1/1 + 2/6) / 2 ≈ 0.667
            assertThat(map10).isGreaterThan(map5);
        }

        @Test
        @DisplayName("检索结果为空 — MAP@5 = 0.0")
        void mapAtK_shouldReturnZero_whenEmptyResults() {
            double map = EvalMetricsCalculator.mapAtK(List.of(), Set.of("doc-a"), 5);

            assertThat(map).isEqualTo(0.0);
        }
    }

    // === Precision@K ===

    @Nested
    @DisplayName("Precision@K")
    class PrecisionAtKTests {

        @Test
        @DisplayName("正常用例 — top-5 中 2 个相关，Precision@5 = 0.4")
        void precisionAtK_shouldCalculateCorrectly() {
            List<String> retrieved = List.of("doc-a", "doc-x", "doc-b", "doc-y", "doc-z");
            Set<String> relevant = Set.of("doc-a", "doc-b");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 5);

            assertThat(precision).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("全部命中 — Precision@5 = 1.0")
        void precisionAtK_shouldReturnOne_whenAllHit() {
            List<String> retrieved = List.of("doc-a", "doc-b", "doc-c");
            Set<String> relevant = Set.of("doc-a", "doc-b", "doc-c");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 3);

            assertThat(precision).isEqualTo(1.0);
        }

        @Test
        @DisplayName("零命中 — Precision@5 = 0.0")
        void precisionAtK_shouldReturnZero_whenNoHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 5);

            assertThat(precision).isEqualTo(0.0);
        }

        @Test
        @DisplayName("相关文档列表为空 — Precision@5 = 1.0（默认值）")
        void precisionAtK_shouldReturnOne_whenNoRelevantDocs() {
            List<String> retrieved = List.of("doc-a", "doc-b");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, Set.of(), 5);

            assertThat(precision).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K=1 单条 — 命中时 Precision@1 = 1.0")
        void precisionAtK_shouldReturnOne_whenSingleResultHit() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-a");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 1);

            assertThat(precision).isEqualTo(1.0);
        }

        @Test
        @DisplayName("K=1 单条 — 未命中时 Precision@1 = 0.0")
        void precisionAtK_shouldReturnZero_whenSingleResultMiss() {
            List<String> retrieved = List.of("doc-a", "doc-b");
            Set<String> relevant = Set.of("doc-x");

            double precision = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 1);

            assertThat(precision).isEqualTo(0.0);
        }

        @Test
        @DisplayName("检索结果为空 — Precision@5 = 0.0")
        void precisionAtK_shouldReturnZero_whenEmptyResults() {
            double precision = EvalMetricsCalculator.precisionAtK(List.of(), Set.of("doc-a"), 5);

            assertThat(precision).isEqualTo(0.0);
        }

        @Test
        @DisplayName("K 截断 — 只算 top-K 内的命中比例")
        void precisionAtK_shouldRespectKLimit() {
            // top-3: doc-a(命中), doc-x, doc-x → 1/3
            // 但 doc-b 在第 5 位，不算入 Precision@3
            List<String> retrieved = List.of("doc-a", "doc-x", "doc-x", "doc-x", "doc-b");
            Set<String> relevant = Set.of("doc-a", "doc-b");

            double precision3 = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 3);
            double precision5 = EvalMetricsCalculator.precisionAtK(retrieved, relevant, 5);

            assertThat(precision3).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(precision5).isCloseTo(2.0 / 5.0, org.assertj.core.data.Offset.offset(1e-9));
        }
    }
}
