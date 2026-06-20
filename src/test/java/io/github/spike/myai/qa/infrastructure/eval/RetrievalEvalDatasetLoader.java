package io.github.spike.myai.qa.infrastructure.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.qa.domain.model.QueryType;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索评测数据集加载器。
 *
 * <p>从 classpath JSON 文件加载 QA pairs 评测数据集。
 * 包含完整格式校验：缺失必填字段抛 IllegalArgumentException，禁止静默失败。</p>
 *
 * @author spike
 * @since 1.0.0
 */
class RetrievalEvalDatasetLoader {

    private static final Set<String> VALID_QUERY_TYPES =
            Set.of("FACTOID", "PROCEDURAL", "COMPARATIVE", "CHITCHAT", "GENERAL");

    private final ObjectMapper objectMapper;

    RetrievalEvalDatasetLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 从 classpath 加载评测数据集。
     *
     * @param resourcePath classpath 资源路径（如 "eval/retrieval-qa-pairs.json"）
     * @return 评测样本列表
     * @throws IllegalArgumentException 格式校验失败时
     */
    List<EvalSample> load(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalArgumentException("评测数据集文件未找到: " + resourcePath);
            }
            return loadFromStream(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("评测数据集解析失败: " + resourcePath, e);
        }
    }

    /**
     * 从输入流加载评测数据集（供测试使用）。
     *
     * @param inputStream JSON 输入流
     * @return 评测样本列表
     * @throws IllegalArgumentException 格式校验失败时
     */
    List<EvalSample> loadFromStream(InputStream inputStream) {
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(
                    inputStream, new TypeReference<>() {});
            return parseSamples(rawList);
        } catch (IOException e) {
            throw new IllegalArgumentException("评测数据集解析失败", e);
        }
    }

    private List<EvalSample> parseSamples(List<Map<String, Object>> rawList) {
        List<EvalSample> samples = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            samples.add(parseOne(rawList.get(i), i));
        }
        return samples;
    }

    @SuppressWarnings("unchecked")
    private EvalSample parseOne(Map<String, Object> raw, int index) {
        String prefix = "样本[" + index + "] ";

        // 校验必填字段
        String question = requireString(raw, "question", prefix);
        String queryTypeStr = requireString(raw, "query_type", prefix);
        List<String> relevantDocIds = requireStringList(raw, "relevant_doc_ids", prefix);

        // 校验 query_type 合法性
        if (!VALID_QUERY_TYPES.contains(queryTypeStr)) {
            throw new IllegalArgumentException(
                    prefix + "query_type 非法值 '" + queryTypeStr
                            + "'，必须为 " + VALID_QUERY_TYPES);
        }
        QueryType queryType = QueryType.valueOf(queryTypeStr);

        // 解析 relevance_levels（可选字段，CHITCHAT 允许为空）
        Map<String, Object> rawLevels = getOptionalMap(raw, "relevance_levels");
        Map<String, RelevanceLevel> relevanceLevels = parseRelevanceLevels(rawLevels, prefix);

        // 校验：每个 relevant_doc_ids 中的 ID 必须有对应标注
        for (String docId : relevantDocIds) {
            if (!relevanceLevels.containsKey(docId)) {
                throw new IllegalArgumentException(
                        prefix + "relevant_doc_ids 中的 '" + docId
                                + "' 在 relevance_levels 中缺少标注");
            }
        }

        return new EvalSample(question, queryType, relevantDocIds, relevanceLevels);
    }

    private String requireString(Map<String, Object> raw, String key, String prefix) {
        Object val = raw.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new IllegalArgumentException(prefix + "缺失必填字段 '" + key + "'");
        }
        return val.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> requireStringList(Map<String, Object> raw, String key, String prefix) {
        Object val = raw.get(key);
        if (val == null) {
            throw new IllegalArgumentException(prefix + "缺失必填字段 '" + key + "'");
        }
        if (!(val instanceof List<?> list)) {
            throw new IllegalArgumentException(prefix + "'" + key + "' 必须是列表类型");
        }
        return list.stream()
                .map(Object::toString)
                .collect(Collectors.toUnmodifiableList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOptionalMap(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, RelevanceLevel> parseRelevanceLevels(
            Map<String, Object> rawLevels, String prefix) {
        var result = new java.util.LinkedHashMap<String, RelevanceLevel>();
        for (Map.Entry<String, Object> entry : rawLevels.entrySet()) {
            String levelStr = entry.getValue().toString().toUpperCase();
            try {
                result.put(entry.getKey(), RelevanceLevel.valueOf(levelStr));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        prefix + "relevance_levels 中 '" + entry.getKey()
                                + "' 的值非法: '" + entry.getValue()
                                + "'，必须为 strong 或 weak");
            }
        }
        return result;
    }
}
