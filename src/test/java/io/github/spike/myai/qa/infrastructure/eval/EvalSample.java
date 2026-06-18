package io.github.spike.myai.qa.infrastructure.eval;

import io.github.spike.myai.qa.domain.model.QueryType;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 检索评测单条样本（QA pair）。
 *
 * <p>封装一条评测查询及其标注的参考文档相关度信息。
 * 用于 EvalRunner 评测数据集加载和指标计算。</p>
 *
 * @param question           用户查询文本
 * @param queryType          查询意图类型
 * @param relevantDocIds     相关文档 ID 列表
 * @param relevanceLevels    文档 ID → 相关度级别映射
 * @author spike
 * @since 1.0.0
 */
record EvalSample(
        String question,
        QueryType queryType,
        List<String> relevantDocIds,
        Map<String, RelevanceLevel> relevanceLevels) {

    EvalSample {
        relevantDocIds = Collections.unmodifiableList(new java.util.ArrayList<>(relevantDocIds));
        relevanceLevels = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(relevanceLevels));
    }
}
