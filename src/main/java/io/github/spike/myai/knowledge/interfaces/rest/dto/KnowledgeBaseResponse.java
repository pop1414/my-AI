package io.github.spike.myai.knowledge.interfaces.rest.dto;

/**
 * 知识库响应体（REST DTO）。
 *
 * <p>该对象用于定义对外 API 的返回结构，与内部应用层结果对象分离，
 * 避免内部领域模型变更直接影响接口契约，符合接口与实现分离的设计原则。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>GET /api/knowledge-bases —— 知识库列表查询，返回该对象的集合</li>
 *   <li>GET /api/knowledge-bases/{id} —— 单个知识库详情查询</li>
 *   <li>POST /api/knowledge-bases —— 创建知识库成功后返回</li>
 *   <li>PUT /api/knowledge-bases/{id} —— 更新知识库成功后返回</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code id}：知识库全局唯一标识，由服务端生成，前端只读</li>
 *   <li>{@code name}：知识库名称，用于前端列表展示及搜索过滤</li>
 *   <li>{@code description}：知识库描述信息，可为空字符串</li>
 *   <li>{@code status}：知识库当前状态（如启用、停用、索引中），
 *        以字符串形式暴露，避免前端依赖枚举类型</li>
 *   <li>{@code indexedDocumentCount}：该知识库下已完成索引的文档数量，
 *        用于前端展示索引进度</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>{@code status} 字段使用 {@link String} 而非领域枚举类型，
 *        确保接口契约稳定性，枚举值变更不影响 API 兼容性</li>
 *   <li>{@code indexedDocumentCount} 为 {@code long} 类型，
 *        支持大规模文档计数，避免整型溢出</li>
 * </ul>
 *
 * <p>注意：该 Record 由 Java 编译器自动生成构造器、访问器、{@code equals}、
 * {@code hashCode} 及 {@code toString} 方法，无需手动编写。
 *
 * @param id                   知识库唯一标识（UUID 格式，服务端生成）
 * @param name                 知识库名称（用于前端展示）
 * @param description          知识库描述（可为空）
 * @param status               知识库状态（字符串形式，如 ACTIVE / DISABLED）
 * @param indexedDocumentCount 已索引文档数量（非负整数）
 * @author Spike
 * @since 1.0.0
 */
public record KnowledgeBaseResponse(
        String id,
        String name,
        String description,
        String status,
        long indexedDocumentCount) {
}
