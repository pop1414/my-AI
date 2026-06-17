package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 可问答版本范围过滤构造器。
 *
 * <p>为 Dense 和 Sparse 两路检索提供统一的 scope 过滤逻辑。
 * Dense 路径使用 {@link #toFilterExpression}（Spring AI Filter.Expression），
 * Sparse 路径使用 {@link #toSqlCondition}（SQL WHERE 片段 + 参数列表）。</p>
 *
 * <p>输入相同（{@code List<AskableDocumentVersion>}），仅输出格式不同。
 * 集中维护避免 D20 Group Model 迁移时遗漏。</p>
 *
 * @author spike
 * @since 1.0.0
 */
class ScopeFilterBuilder {

    /** 向量文档元数据中"文档 ID"字段名。 */
    private static final String METADATA_DOCUMENT_ID = "documentId";
    /** 向量文档元数据中"文档版本号"字段名。 */
    private static final String METADATA_DOCUMENT_VERSION_NUMBER = "documentVersionNumber";
    /** 向量文档元数据中"分块版本"字段名。 */
    private static final String METADATA_SPLIT_VERSION = "splitVersion";
    /** 初始文档版本号。 */
    private static final int INITIAL_DOCUMENT_VERSION_NUMBER = 1;
    /** 历史初始向量使用的分块版本号。 */
    private static final String LEGACY_INITIAL_SPLIT_VERSION = "v1";

    private ScopeFilterBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 构造 Spring AI Filter.Expression 格式的范围过滤表达式。
     *
     * <p>每个 document 构造成成对条件：
     * {@code (documentId = A AND (version = N OR splitVersion = "version-N-v1" [...OR splitVersion = "v1"]))}，
     * 多文档间以 OR 组合。</p>
     *
     * @param scope 可问答文档版本范围
     * @return 过滤表达式；scope 为空时返回 null
     */
    static Filter.Expression toFilterExpression(List<AskableDocumentVersion> scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> documentVersionFilters = new ArrayList<>();
        for (AskableDocumentVersion item : scope) {
            documentVersionFilters.add(builder.and(
                    builder.eq(METADATA_DOCUMENT_ID, item.documentId()),
                    builder.group(buildVersionFilterExpression(builder, item.askableVersionNumber()))));
        }
        FilterExpressionBuilder.Op result = documentVersionFilters.getFirst();
        for (int i = 1; i < documentVersionFilters.size(); i++) {
            result = builder.or(result, documentVersionFilters.get(i));
        }
        return result.build();
    }

    /**
     * 构造 SQL WHERE 条件片段格式的范围过滤条件。
     *
     * <p>语义与 {@link #toFilterExpression} 完全等价，输出为 SQL 片段 + 参数列表。
     * metadata 列为 PostgreSQL json 类型，使用 {@code metadata->>'key'} 提取字段值。</p>
     *
     * @param scope 可问答文档版本范围
     * @return SQL 条件片段；scope 为空时返回空 whereClause 和空 params
     */
    static SqlScopeCondition toSqlCondition(List<AskableDocumentVersion> scope) {
        if (scope == null || scope.isEmpty()) {
            return new SqlScopeCondition("", List.of());
        }

        StringBuilder whereClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (int i = 0; i < scope.size(); i++) {
            AskableDocumentVersion item = scope.get(i);
            if (i > 0) {
                whereClause.append(" OR ");
            }
            whereClause.append("(");
            // documentId 条件
            whereClause.append("metadata->>'").append(METADATA_DOCUMENT_ID).append("' = ?");
            params.add(item.documentId());
            whereClause.append(" AND (");
            // version 条件
            appendVersionSqlCondition(whereClause, params, item.askableVersionNumber());
            whereClause.append("))");
        }

        return new SqlScopeCondition("(" + whereClause.toString() + ")", params);
    }

    /**
     * 构造单个文档版本的 FilterExpressionBuilder 表达式。
     */
    private static FilterExpressionBuilder.Op buildVersionFilterExpression(
            FilterExpressionBuilder builder,
            int askableVersionNumber) {
        FilterExpressionBuilder.Op versionFilter = builder.or(
                builder.eq(METADATA_DOCUMENT_VERSION_NUMBER, askableVersionNumber),
                builder.eq(METADATA_SPLIT_VERSION, "version-" + askableVersionNumber + "-v1"));
        if (askableVersionNumber == INITIAL_DOCUMENT_VERSION_NUMBER) {
            versionFilter = builder.or(versionFilter, builder.eq(METADATA_SPLIT_VERSION, LEGACY_INITIAL_SPLIT_VERSION));
        }
        return versionFilter;
    }

    /**
     * 追加单个文档版本的 SQL 条件到 StringBuilder。
     */
    private static void appendVersionSqlCondition(
            StringBuilder whereClause,
            List<Object> params,
            int askableVersionNumber) {
        // documentVersionNumber = N
        whereClause.append("(metadata->>'").append(METADATA_DOCUMENT_VERSION_NUMBER).append("')::int = ?");
        params.add(askableVersionNumber);
        // splitVersion = "version-N-v1"
        whereClause.append(" OR metadata->>'").append(METADATA_SPLIT_VERSION).append("' = ?");
        params.add("version-" + askableVersionNumber + "-v1");
        // legacy: splitVersion = "v1" (仅 version=1)
        if (askableVersionNumber == INITIAL_DOCUMENT_VERSION_NUMBER) {
            whereClause.append(" OR metadata->>'").append(METADATA_SPLIT_VERSION).append("' = ?");
            params.add(LEGACY_INITIAL_SPLIT_VERSION);
        }
    }
}
