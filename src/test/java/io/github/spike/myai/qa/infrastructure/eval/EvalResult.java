package io.github.spike.myai.qa.infrastructure.eval;

import io.github.spike.myai.qa.domain.model.QueryType;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检索评测单条结果。
 *
 * <p>封装一次评测查询的检索结果和指标得分。</p>
 *
 * @param question        用户查询文本
 * @param queryType       查询意图类型
 * @param retrievedIds    检索返回的文档 ID 列表
 * @param relevantDocIds  标注的相关文档 ID 列表
 * @param relevanceLevels 文档 ID → 相关度级别映射
 * @param recall          Recall@K 得分
 * @param mrr             MRR 得分
 * @param hitRate         HitRate@K 得分
 * @param latencyMs       本次检索耗时（毫秒）
 * @author spike
 * @since 1.0.0
 */
record EvalResult(
        String question,
        QueryType queryType,
        List<String> retrievedIds,
        List<String> relevantDocIds,
        Map<String, RelevanceLevel> relevanceLevels,
        double recall,
        double mrr,
        double hitRate,
        long latencyMs) {

    EvalResult {
        retrievedIds = Collections.unmodifiableList(new java.util.ArrayList<>(retrievedIds));
        relevantDocIds = Collections.unmodifiableList(new java.util.ArrayList<>(relevantDocIds));
        relevanceLevels = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(relevanceLevels));
    }
}
