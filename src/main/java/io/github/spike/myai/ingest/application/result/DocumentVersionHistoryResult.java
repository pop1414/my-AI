package io.github.spike.myai.ingest.application.result;

import java.util.List;

/**
 * 文档版本历史应用层返回结果。
 *
 * <p>聚合了一个文档资产下所有版本历史的完整视图，
 * 包含排序契约声明和版本列表。版本列表允许为空集合（文档存在但无版本记录）。
 *
 * @param documentId 文档资产 ID，不可为空
 * @param sort       版本历史排序契约（如 "versionNumber,DESC"），不可为空
 * @param versions   版本历史列表，不可为 null（可为空集合）
 */
public record DocumentVersionHistoryResult(
        String documentId,
        String sort,
        List<DocumentVersionHistoryItemResult> versions) {

    /**
     * 紧凑构造器：在 record 实例化时对必填字段进行非空校验。
     *
     * <p>校验策略：
     * <ul>
     *   <li>{@code documentId} —— 不可为空或空白，保证下游可追溯数据来源；</li>
     *   <li>{@code sort} —— 不可为空或空白，保证前端可理解返回的排序语义；</li>
     *   <li>{@code versions} —— 不可为 null；允许空集合（表示该文档暂无版本记录）。</li>
     * </ul>
     *
     * @throws IllegalArgumentException 当任一必填字段不符合要求时
     */
    public DocumentVersionHistoryResult {
        // 校验 documentId：必须为非空字符串，标识数据归属
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        // 校验 sort：排序契约字符串不可为空，保证 API 契约一致性
        if (sort == null || sort.isBlank()) {
            throw new IllegalArgumentException("sort must not be blank");
        }
        // 校验 versions：列表本身不可为 null（允许内部无元素）
        if (versions == null) {
            throw new IllegalArgumentException("versions must not be null");
        }
    }
}
