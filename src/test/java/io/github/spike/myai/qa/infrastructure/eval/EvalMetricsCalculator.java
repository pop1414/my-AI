package io.github.spike.myai.qa.infrastructure.eval;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 检索评测指标计算器。
 *
 * <p>纯工具类 — 无状态、零 Spring 依赖，所有指标计算均为 static 纯函数。
 * 对齐 BEIR 评测体系，支持 6 大标准指标：</p>
 * <ul>
 *   <li>Recall@K — 召回率</li>
 *   <li>MRR — 平均倒数排名</li>
 *   <li>HitRate@K — 命中率</li>
 *   <li>NDCG@K — 归一化折损累积增益（分级相关度加权）</li>
 *   <li>MAP@K — 平均精度均值</li>
 *   <li>Precision@K — 精确率</li>
 * </ul>
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
     * <p>使用分级相关度加权：STRONG=2, WEAK=1, 无标注=0。
     * 公式：NDCG@K = DCG@K / IDCG@K，其中 DCG@K = Σ gain(i) / log2(i+1)。
     * 相关文档列表为空时默认返回 1.0（CHITCHAT 兜底）。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param relevanceLevels   文档 ID → 相关度级别映射
     * @param k                 截取前 K 条结果
     * @return NDCG@K 值（0.0 ~ 1.0）
     */
    static double ndcgAtK(
            List<String> retrievedIds,
            Map<String, RelevanceLevel> relevanceLevels,
            int k) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(relevanceLevels, "relevanceLevels 不能为 null");
        if (k < 0) {
            throw new IllegalArgumentException("k 必须 >= 0, 当前值: " + k);
        }
        if (relevanceLevels.isEmpty()) {
            return 1.0;
        }

        // 计算 DCG@K
        double dcg = 0.0;
        int limit = Math.min(k, retrievedIds.size());
        for (int i = 0; i < limit; i++) {
            double gain = toGain(relevanceLevels.get(retrievedIds.get(i)));
            dcg += gain / log2(i + 2); // i 是 0-based，position=i+1，折损=log2(position+1)=log2(i+2)
        }

        // 计算 IDCG@K（理想排序：按 gain 降序排列）
        double idcg = 0.0;
        List<Double> idealGains = relevanceLevels.values().stream()
                .map(EvalMetricsCalculator::toGain)
                .filter(g -> g > 0)
                .sorted(java.util.Comparator.reverseOrder())
                .limit(k)
                .toList();
        for (int i = 0; i < idealGains.size(); i++) {
            idcg += idealGains.get(i) / log2(i + 2);
        }

        return idcg == 0.0 ? 1.0 : dcg / idcg;
    }

    /**
     * 计算 MAP@K（Mean Average Precision）。
     *
     * <p>对每个 strong 相关文档计算 Precision@rank，取平均值。
     * 仅考虑 strong 级别相关文档。相关文档列表为空时默认返回 1.0。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param strongRelevantIds strong 相关文档 ID 集合
     * @param k                 截取前 K 条结果
     * @return MAP@K 值（0.0 ~ 1.0）
     */
    static double mapAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k) {
        Objects.requireNonNull(retrievedIds, "retrievedIds 不能为 null");
        Objects.requireNonNull(strongRelevantIds, "strongRelevantIds 不能为 null");
        if (k < 0) {
            throw new IllegalArgumentException("k 必须 >= 0, 当前值: " + k);
        }
        if (strongRelevantIds.isEmpty()) {
            return 1.0;
        }
        int limit = Math.min(k, retrievedIds.size());
        double sumPrecision = 0.0;
        int hitCount = 0;
        for (int i = 0; i < limit; i++) {
            if (strongRelevantIds.contains(retrievedIds.get(i))) {
                hitCount++;
                // Precision@rank = 命中数 / 当前排名（1-based）
                sumPrecision += (double) hitCount / (i + 1);
            }
        }
        return hitCount == 0 ? 0.0 : sumPrecision / strongRelevantIds.size();
    }

    /**
     * 计算 Precision@K。
     *
     * <p>top-K 结果中 strong 相关文档的比例。
     * 相关文档列表为空时默认返回 1.0。</p>
     *
     * @param retrievedIds      检索返回的文档 ID 列表（按排名排序）
     * @param strongRelevantIds strong 相关文档 ID 集合
     * @param k                 截取前 K 条结果
     * @return Precision@K 值（0.0 ~ 1.0）
     */
    static double precisionAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k) {
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
        return (double) hitCount / k;
    }

    /**
     * 相关度级别转换为 NDCG gain 值。
     * <ul>
     *   <li>STRONG → 2.0</li>
     *   <li>WEAK → 1.0</li>
     *   <li>null（无标注）→ 0.0</li>
     * </ul>
     */
    private static double toGain(RelevanceLevel level) {
        if (level == null) {
            return 0.0;
        }
        return switch (level) {
            case STRONG -> 2.0;
            case WEAK -> 1.0;
        };
    }

    /**
     * 以 2 为底的对数，用于 NDCG 折损计算。
     * 调用处传入 (0-based index + 2)，即 (position + 1)，确保分母 >= 1。
     */
    private static double log2(int position) {
        return Math.log(position) / Math.log(2);
    }
}
