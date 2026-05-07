package io.github.spike.myai.knowledge.domain.model;

/**
 * 知识库列表摘要视图（读模型 / Read Model）。
 *
 * <p>该 Record 是 {@link KnowledgeBase} 聚合根的投影视图，
 * 专用于知识库列表查询场景，与写模型分离：
 * <ul>
 *   <li>不包含 {@code createdAt / updatedAt} 等审计字段，
 *       减少列表接口的数据传输量；</li>
 *   <li>额外包含 {@code indexedDocumentCount} 聚合统计字段，
 *       由仓库层在查询时通过关联计算得出。</li>
 * </ul>
 *
 * <h3>与 {@link KnowledgeBase} 的区别</h3>
 * <table>
 *   <tr><th>维度</th><th>KnowledgeBase（聚合根）</th><th>KnowledgeBaseSummary（视图）</th></tr>
 *   <tr><td>用途</td><td>创建/更新等写操作</td><td>列表查询等读操作</td></tr>
 *   <tr><td>时间戳</td><td>含 createdAt / updatedAt</td><td>不含</td></tr>
 *   <tr><td>文档计数</td><td>不含</td><td>含 indexedDocumentCount</td></tr>
 *   <tr><td>不变性校验</td><td>紧凑构造器强校验</td><td>无（数据来自仓库层，已保证有效）</td></tr>
 * </table>
 *
 * @param kbId                 知识库业务键
 * @param name                 展示名称
 * @param description          描述信息
 * @param status               当前生命周期状态
 * @param indexedDocumentCount 已索引文档数量（聚合统计，非负整数）
 * @author Spike
 * @since 1.0.0
 */
public record KnowledgeBaseSummary(
        String kbId,
        String name,
        String description,
        KnowledgeBaseStatus status,
        long indexedDocumentCount) {
}
