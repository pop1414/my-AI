package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.util.List;

/**
 * 文档列表分页响应体（REST DTO）。
 *
 * <p>该 Record 封装文档分页查询的完整响应结构，
 * 包含当前页的数据列表与分页元信息，供前端实现分页控件。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code items} —— 当前页的文档列表项集合，可能为空列表；</li>
 *   <li>{@code total} —— 符合当前筛选条件的文档总数（非当前页数量），
 *       用于前端计算总页数（total / limit 向上取整）；</li>
 *   <li>{@code limit} —— 当前请求的每页条数（与请求参数一致，回显给前端）；</li>
 *   <li>{@code offset} —— 当前请求的偏移量（与请求参数一致，回显给前端）。</li>
 * </ul>
 *
 * <h3>分页计算示例</h3>
 * <pre>
 *   totalPages = (int) Math.ceil((double) total / limit);
 *   currentPage = offset / limit + 1;  // 假设 offset 从 0 开始
 * </pre>
 *
 * <p>注意：该 Record 由 Java 编译器自动生成构造器、访问器、
 * {@code equals}、{@code hashCode} 及 {@code toString} 方法。
 *
 * @param items  当前页文档列表项集合（可能为空）
 * @param total  符合筛选条件的总记录数
 * @param limit  每页条数（回显请求参数）
 * @param offset 偏移量（回显请求参数）
 * @author Spike
 * @since 1.0.0
 */
public record DocumentListPageResponse(
        List<DocumentListItemResponse> items,
        long total,
        int limit,
        int offset) {
}
