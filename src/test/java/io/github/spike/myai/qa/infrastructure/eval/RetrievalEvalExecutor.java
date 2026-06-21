package io.github.spike.myai.qa.infrastructure.eval;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索评测执行器。
 *
 * <p>封装检索调用逻辑，支持批量执行单模式检索评测。
 * 隔离评测逻辑与业务检索接口，使用 ChunkRetrievalPort 接口调用。</p>
 *
 * <p>检索结果按 documentId 去重后计算指标，避免同一文档的多个 chunk
 * 导致 Recall > 1.0 的指标失真问题。</p>
 *
 * @author spike
 * @since 1.0.0
 */
class RetrievalEvalExecutor {

    private static final int DEFAULT_TOP_K = 5;

    /**
     * 执行单模式批量检索评测。
     *
     * @param adapter  检索适配器（Dense / Sparse / Hybrid）
     * @param dataset  评测数据集
     * @param topK     检索返回条数
     * @return 评测结果列表（每条对应一个 QA pair）
     */
    List<EvalResult> executeSingleMode(
            ChunkRetrievalPort adapter,
            List<EvalSample> dataset,
            int topK) {

        List<EvalResult> results = new ArrayList<>();
        for (EvalSample sample : dataset) {
            try {
                results.add(executeOne(adapter, sample, topK));
            } catch (Exception e) {
                results.add(new EvalResult(
                        sample.question(),
                        sample.queryType(),
                        List.of(),
                        sample.relevantDocIds(),
                        sample.relevanceLevels(),
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                        -1)); // latencyMs=-1 标记检索失败
            }
        }
        return results;
    }

    /**
     * 执行单模式批量检索评测（默认 topK=5）。
     */
    List<EvalResult> executeSingleMode(
            ChunkRetrievalPort adapter,
            List<EvalSample> dataset) {
        return executeSingleMode(adapter, dataset, DEFAULT_TOP_K);
    }

    private EvalResult executeOne(ChunkRetrievalPort adapter, EvalSample sample, int topK) {
        // 仅统计 strong 相关文档
        Set<String> strongRelevantIds = sample.relevanceLevels().entrySet().stream()
                .filter(e -> e.getValue() == RelevanceLevel.STRONG)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        long startTime = System.currentTimeMillis();
        List<RetrievedChunk> chunks = adapter.similaritySearch(sample.question(), topK);
        long latencyMs = System.currentTimeMillis() - startTime;

        if (chunks == null) {
            chunks = List.of();
        }

        // 按 documentId 去重，保留首次出现的排名（同文档多个 chunk 只算一次）
        List<String> retrievedIds = chunks.stream()
                .map(RetrievedChunk::documentId)
                .distinct()
                .toList();

        // BEIR 六大指标
        double recall = EvalMetricsCalculator.recallAtK(retrievedIds, strongRelevantIds, topK);
        double mrrValue = EvalMetricsCalculator.mrr(retrievedIds, strongRelevantIds);
        double hitRate = EvalMetricsCalculator.hitRateAtK(retrievedIds, strongRelevantIds, topK);
        double ndcg = EvalMetricsCalculator.ndcgAtK(retrievedIds, sample.relevanceLevels(), topK);
        double map = EvalMetricsCalculator.mapAtK(retrievedIds, strongRelevantIds, topK);
        double precision = EvalMetricsCalculator.precisionAtK(retrievedIds, strongRelevantIds, topK);

        return new EvalResult(
                sample.question(),
                sample.queryType(),
                retrievedIds,
                sample.relevantDocIds(),
                sample.relevanceLevels(),
                recall,
                mrrValue,
                hitRate,
                ndcg,
                map,
                precision,
                latencyMs);
    }
}
