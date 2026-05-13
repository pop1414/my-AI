package io.github.spike.myai.ingest.application.query;

/**
 * 查询指定文档版本历史的应用层 Query。
 *
 * <p>该 Query 对象是不可变数据传输载体（DTO），用于将入参从 Interface 层
 * 传递至 Application 层。在构造时即完成参数校验，确保非法数据不会进入用例层。
 *
 * @param documentId 文档资产 ID，不可为空或空白字符串
 */
public record ListDocumentVersionsQuery(String documentId) {

    /**
     * 紧凑构造器（Compact Constructor）：在 record 实例化时自动执行参数校验。
     *
     * <p>校验规则：
     * <ul>
     *   <li>{@code documentId} 不可为 {@code null}；</li>
     *   <li>{@code documentId} 不可为空白字符串（仅含空格、制表符等）。</li>
     * </ul>
     *
     * @throws IllegalArgumentException 当 documentId 为空或空白时
     */
    public ListDocumentVersionsQuery {
        // 前置校验：documentId 为空或空白时直接拒绝，快速失败
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }

    /**
     * 返回去除首尾空格后的标准化 documentId。
     *
     * <p>该方法用于在下游用例中统一使用标准化后的标识符，
     * 避免因前端误传空格导致查询失败。
     *
     * @return 标准化后的文档 ID 字符串
     */
    public String normalizedDocumentId() {
        return documentId.trim();
    }
}
