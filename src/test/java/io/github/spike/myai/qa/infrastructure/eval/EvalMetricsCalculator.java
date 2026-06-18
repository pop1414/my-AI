package io.github.spike.myai.qa.infrastructure.eval;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 检索评测指标计算器。
 *
 * <p>纯工具类 — 无状态、零 Spring 依赖，所有指标计算均为 static 纯函数。
 * 支持 Recall@5、MRR、HitRate@5 三个核心指标，NDCG@K 预留 Phase 2。</p>
 *
 * @author spike
 * @since 1.0.0
 */
final class EvalMetricsCalculator {

    private EvalMetricsCalculator() {
        // 工具类，禁止实例化
    }

    /**
     * 计算 Recall@K。
     *
     * <p>top-K 中命中 strong 相关文档的比例。
     * 相关文档列表为空时默认返回 1.0（没有参考答案时认为全部命中）。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param strongRelevantIds strong 相关文档 ID 集合
     * @param k                 截取前 K 条结果
     * @return Recall@K 值（0.0 ~ 1.0）
     */
    static double recallAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(strongRelevantIds, "strongRelevantIds 不能为 null");
        if (k < 0) {
            throw new IllegalArgumentException("k 必须 >= 0, 当前值: " + k);
        }
        if (strongRelevantIds.isEmpty()) {
            return 1.0;
        }
        long hitCount = retrievedIds.stream()
                .limit(k)
                .filter(strongRelevantIds::contains)
                .count();
        return (double) hitCount / strongRelevantIds.size();
    }

    /**
     * 计算 MRR（Mean Reciprocal Rank）。
     *
     * <p>第一个 strong 相关结果排名的倒数。
     * 无任何命中时返回 0.0。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param strongRelevantIds strong 相关文档 ID 集合
     * @return MRR 值（0.0 或 1/rank）
     */
    static double mrr(List<String> retrievedIds, Set<String> strongRelevantIds) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(strongRelevantIds, "strongRelevantIds 不能为 null");
        if (strongRelevantIds.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (strongRelevantIds.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * 计算 HitRate@K。
     *
     * <p>top-K 中至少命中 1 条 strong 相关文档时返回 1.0，否则 0.0。
     * 相关文档列表为空时默认返回 1.0。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param strongRelevantIds strong 相关文档 ID 集合
     * @param k                 截取前 K 条结果
     * @return HitRate@K 值（0.0 或 1.0）
     */
    static double hitRateAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(strongRelevantIds, "strongRelevantIds 不能为 null");
        if (k < 0) {
            throw new IllegalArgumentException("k 必须 >= 0, 当前值: " + k);
        }
        if (strongRelevantIds.isEmpty()) {
            return 1.0;
        }
        boolean hasHit = retrievedIds.stream()
                .limit(k)
                .anyMatch(strongRelevantIds::contains);
        return hasHit ? 1.0 : 0.0;
    }

    /**
     * 计算 NDCG@K（Normalized Discounted Cumulative Gain）。
     *
     * <p>Phase 2 预留接口 — 当前抛出 UnsupportedOperationException。
     * 后续实现无需重构现有代码。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表
     * @param relevanceLevels   文档 ID → 相关度级别映射
     * @param k                 截取前 K 条结果
     * @return NDCG@K 值
     * @throws UnsupportedOperationException Phase 2 实现
     */
    static double ndcgAtK(
            List<String> retrievedIds,
            java.util.Map<String, RelevanceLevel> relevanceLevels,
            int k) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(relevanceLevels, "relevanceLevels 不能为 null");
        if (k < 0) {
            throw new IllegalArgumentException("k 必须 >= 0, 当前值: " + k);
        }
        throw new UnsupportedOperationException("NDCG@K: Phase 2");
    }
}
