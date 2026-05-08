package io.github.spike.myai.knowledge.interfaces.rest.dto;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;

/**
 * 编辑（更新）知识库请求体（REST DTO）。
 *
 * <p>该对象用于接收前端更新知识库的 HTTP 请求参数，
 * 作为接口层的入参载体，与内部领域模型解耦。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>PUT /api/knowledge-bases/{id} 全量更新指定知识库</li>
 *   <li>PATCH /api/knowledge-bases/{id} 部分更新指定知识库</li>
 * </ul>
 *
 * <h3>字段约束</h3>
 * <ul>
 *   <li>{@code name}：选填，更新后的知识库名称，需保证唯一性，建议长度 1~128 字符；
 *        若为 {@code null} 则保持原名称不变</li>
 *   <li>{@code description}：选填，更新后的知识库描述；若为 {@code null} 则保持原描述不变</li>
 *   <li>{@code status}：选填，更新后的知识库状态，用于控制启用/停用等生命周期；
 *        若为 {@code null} 则保持原状态不变</li>
 * </ul>
 *
 * <p>与 {@link CreateKnowledgeBaseRequest} 的区别：更新操作需要配合路径参数
 * {@code id} 指定目标知识库，且各字段允许为 {@code null} 表示不修改该字段。
 *
 * <p>注意：该 Record 由 Java 编译器自动生成构造器、访问器、{@code equals}、
 * {@code hashCode} 及 {@code toString} 方法，无需手动编写。
 *
 * @param name        更新后的知识库名称（为 {@code null} 时保持不变）
 * @param description 更新后的知识库描述（为 {@code null} 时保持不变）
 * @param status      更新后的知识库状态（为 {@code null} 时保持不变）
 * @author Spike
 * @since 1.0.0
 */
public record UpdateKnowledgeBaseRequest(
        String name,
        String description,
        KnowledgeBaseStatus status) {
}
