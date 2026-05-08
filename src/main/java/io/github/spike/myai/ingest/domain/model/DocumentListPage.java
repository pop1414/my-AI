package io.github.spike.myai.ingest.domain.model;

import java.util.List;

/**
 * 文档列表分页结果（Domain Value Object / Read Model）。
 *
 * <p>该 Record 封装文档分页查询的完整结果，
 * 包含当前页的数据列表与分页元信息。
 * 由仓储实现直接返回，不经由聚合根。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code items} —— 当前页的文档列表项集合（不可为 {@code null}，
 *       空列表表示无数据）；</li>
 *   <li>{@code total} —— 符合筛选条件的总记录数（非当前页数量），
 *       用于前端计算总页数；</li>
 *   <li>{@code limit} —— 当前请求的每页条数（正整数，回显请求参数）；</li>
 *   <li>{@code offset} —— 当前请求的偏移量（非负整数，回显请求参数）。</li>
 * </ul>
 *
 * <h3>不变性约束</h3>
 * <ul>
 *   <li>{@code items} 不可为 {@code null}（保证调用方无需判空）；</li>
 *   <li>{@code total} 为非负整数；</li>
 *   <li>{@code limit} 为正整数；</li>
 *   <li>{@code offset} 为非负整数。</li>
 * </ul>
 *
 * @param items  当前页文档列表项（不可为 {@code null}，可为空列表）
 * @param total  总记录数（非负）
 * @param limit  每页条数（正数）
 * @param offset 偏移量（非负）
 * @author Spike
 * @since 1.0.0
 */
public record DocumentListPage(
        List<DocumentListItem> items,
        long total,
        int limit,
        int offset) {

    /**
     * 紧凑构造器：确保分页结果的核心字段合法。
     *
     * <p>保证调用方（应用层、接口层）无需对 {@code items} 判空，
     * 且分页元数据始终有效。
     *
     * @throws IllegalArgumentException 当字段不满足约束时
     */
    public DocumentListPage {
        // items 不可为 null：保证调用方可安全迭代，空列表用 Collections.emptyList()
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        // total 不可为负：总记录数至少为 0
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        // limit 必须为正：防止 SQL LIMIT 0 或无意义的分页
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // offset 不可为负：防止 SQL OFFSET 负值
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
