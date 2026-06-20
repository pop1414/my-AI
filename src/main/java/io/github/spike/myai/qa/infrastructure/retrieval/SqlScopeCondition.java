package io.github.spike.myai.qa.infrastructure.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SQL 范围过滤条件。
 *
 * <p>由 {@link ScopeFilterBuilder#toSqlCondition} 生成，供 Sparse 检索路径
 * 拼接 WHERE 子句使用。{@code whereClause} 不含 WHERE 关键字，且始终有外层括号包裹，
 * 调用方可安全拼接附加条件（如 {@code WHERE status = ? AND <whereClause>}）。</p>
 *
 * @param whereClause SQL 条件片段（不含 WHERE 关键字，始终有外层括号），scope 为空时为空字符串
 * @param params      条件参数列表，顺序与 {@code ?} 占位符一一对应
 * @author spike
 * @since 1.0.0
 */
record SqlScopeCondition(String whereClause, List<Object> params) {

    SqlScopeCondition {
        Objects.requireNonNull(whereClause, "whereClause must not be null");
        Objects.requireNonNull(params, "params must not be null");
        params = Collections.unmodifiableList(new ArrayList<>(params));
    }
}
