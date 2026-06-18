package io.github.spike.myai.qa.infrastructure.eval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.spike.myai.qa.domain.model.QueryType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索评测报告生成器。
 *
 * <p>生成三层 JSON 报告结构：
 * 1. 整体汇总层 — Recall@5、MRR、HitRate@5、总查询数、平均检索耗时
 * 2. 分类型统计层 — 按 QueryType 分组，每类的三项指标均值
 * 3. 单条详情层 — 查询内容、query_type、检索返回 ID 列表、标注 ID 列表、命中标记、单条指标</p>
 *
 * @author spike
 * @since 1.0.0
 */
class EvalReportGenerator {

    private final ObjectMapper objectMapper;

    EvalReportGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * 生成单模式评测报告 JSON 字符串。
     *
     * @param modeName 模式名称（如 "dense"、"sparse"、"hybrid"）
     * @param results  评测结果列表
     * @return 格式化的 JSON 字符串
     */
    String generateSingleModeJson(String modeName, List<EvalResult> results) {
        Map<String, Object> report = buildSingleModeReport(modeName, results);
        return toJson(report);
    }

    /**
     * 生成三模式对比报告 JSON 字符串。
     *
     * @param denseResults  Dense 模式评测结果
     * @param sparseResults Sparse 模式评测结果
     * @param hybridResults Hybrid 模式评测结果
     * @return 格式化的 JSON 字符串
     */
    String generateComparisonJson(
            List<EvalResult> denseResults,
            List<EvalResult> sparseResults,
            List<EvalResult> hybridResults) {

        Map<String, Object> report = new LinkedHashMap<>();

        // 整体汇总层：三个模式的独立汇总 + 对比表
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_queries", denseResults.size());
        summary.put("modes", Map.of(
                "dense", buildModeSummary(denseResults),
                "sparse", buildModeSummary(sparseResults),
                "hybrid", buildModeSummary(hybridResults)));
        report.put("summary", summary);

        // 分类型统计层
        Map<String, Object> byQueryType = new LinkedHashMap<>();
        for (QueryType type : QueryType.values()) {
            Map<String, Object> typeEntry = buildQueryTypeComparison(type,
                    denseResults, sparseResults, hybridResults);
            if (!typeEntry.isEmpty()) {
                byQueryType.put(type.name(), typeEntry);
            }
        }
        report.put("by_query_type", byQueryType);

        // 单条详情层
        report.put("details", buildComparisonDetails(denseResults, sparseResults, hybridResults));

        return toJson(report);
    }

    private Map<String, Object> buildSingleModeReport(String modeName, List<EvalResult> results) {
        Map<String, Object> report = new LinkedHashMap<>();

        // 整体汇总
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_queries", results.size());
        summary.put("mode", modeName);
        summary.putAll(buildModeSummary(results));
        report.put("summary", summary);

        // 分类型统计
        Map<String, Object> byQueryType = new LinkedHashMap<>();
        for (QueryType type : QueryType.values()) {
            Map<String, Object> typeStats = buildQueryTypeStats(type, results);
            if (!typeStats.isEmpty()) {
                byQueryType.put(type.name(), typeStats);
            }
        }
        report.put("by_query_type", byQueryType);

        // 单条详情
        report.put("details", buildDetails(results));

        return report;
    }

    private Map<String, Object> buildModeSummary(List<EvalResult> results) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (results.isEmpty()) {
            return summary;
        }
        summary.put("recall_at_5", avg(results, EvalResult::recall));
        summary.put("mrr", avg(results, EvalResult::mrr));
        summary.put("hit_rate_at_5", avg(results, EvalResult::hitRate));
        summary.put("avg_latency_ms", avgLong(results, EvalResult::latencyMs));
        return summary;
    }

    private Map<String, Object> buildQueryTypeStats(QueryType type, List<EvalResult> results) {
        List<EvalResult> filtered = results.stream()
                .filter(r -> r.queryType() == type)
                .toList();
        if (filtered.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", filtered.size());
        stats.put("recall_at_5", avg(filtered, EvalResult::recall));
        stats.put("mrr", avg(filtered, EvalResult::mrr));
        stats.put("hit_rate_at_5", avg(filtered, EvalResult::hitRate));
        return stats;
    }

    private Map<String, Object> buildQueryTypeComparison(
            QueryType type,
            List<EvalResult> denseResults,
            List<EvalResult> sparseResults,
            List<EvalResult> hybridResults) {

        Map<String, Object> denseStats = buildQueryTypeStats(type, denseResults);
        Map<String, Object> sparseStats = buildQueryTypeStats(type, sparseResults);
        Map<String, Object> hybridStats = buildQueryTypeStats(type, hybridResults);

        if (denseStats.isEmpty() && sparseStats.isEmpty() && hybridStats.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> comparison = new LinkedHashMap<>();
        if (!denseStats.isEmpty()) comparison.put("dense", denseStats);
        if (!sparseStats.isEmpty()) comparison.put("sparse", sparseStats);
        if (!hybridStats.isEmpty()) comparison.put("hybrid", hybridStats);
        return comparison;
    }

    private List<Map<String, Object>> buildDetails(List<EvalResult> results) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (EvalResult r : results) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("question", r.question());
            detail.put("query_type", r.queryType().name());
            detail.put("retrieved_ids", r.retrievedIds());
            detail.put("relevant_ids", r.relevantDocIds());
            detail.put("recall", r.recall());
            detail.put("mrr", r.mrr());
            detail.put("hit_rate", r.hitRate());
            detail.put("latency_ms", r.latencyMs());
            details.add(detail);
        }
        return details;
    }

    private List<Map<String, Object>> buildComparisonDetails(
            List<EvalResult> denseResults,
            List<EvalResult> sparseResults,
            List<EvalResult> hybridResults) {

        int maxSize = Math.max(denseResults.size(),
                Math.max(sparseResults.size(), hybridResults.size()));
        List<Map<String, Object>> details = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            Map<String, Object> detail = new LinkedHashMap<>();
            EvalResult dense = i < denseResults.size() ? denseResults.get(i) : null;
            EvalResult sparse = i < sparseResults.size() ? sparseResults.get(i) : null;
            EvalResult hybrid = i < hybridResults.size() ? hybridResults.get(i) : null;

            if (dense != null) {
                detail.put("question", dense.question());
                detail.put("query_type", dense.queryType().name());
            }

            Map<String, Object> modes = new LinkedHashMap<>();
            if (dense != null) modes.put("dense", buildModeDetail(dense));
            if (sparse != null) modes.put("sparse", buildModeDetail(sparse));
            if (hybrid != null) modes.put("hybrid", buildModeDetail(hybrid));
            detail.put("modes", modes);

            details.add(detail);
        }
        return details;
    }

    private Map<String, Object> buildModeDetail(EvalResult r) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("retrieved_ids", r.retrievedIds());
        detail.put("relevant_ids", r.relevantDocIds());
        detail.put("recall", r.recall());
        detail.put("mrr", r.mrr());
        detail.put("hit_rate", r.hitRate());
        detail.put("latency_ms", r.latencyMs());
        return detail;
    }

    private double avg(List<EvalResult> results, java.util.function.ToDoubleFunction<EvalResult> getter) {
        return results.stream()
                .mapToDouble(getter)
                .average()
                .orElse(0.0);
    }

    private long avgLong(List<EvalResult> results, java.util.function.ToLongFunction<EvalResult> getter) {
        return Math.round(results.stream()
                .mapToLong(getter)
                .average()
                .orElse(0.0));
    }

    private String toJson(Object report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("报告 JSON 序列化失败", e);
        }
    }
}
