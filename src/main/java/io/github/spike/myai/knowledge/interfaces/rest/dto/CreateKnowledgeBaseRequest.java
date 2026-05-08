package io.github.spike.myai.knowledge.interfaces.rest.dto;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;

/**
 * 创建知识库请求体（REST DTO）。
 *
 * <p>该对象用于接收前端创建知识库的 HTTP 请求参数，
 * 作为接口层的入参载体，与内部领域模型解耦。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>POST /api/knowledge-bases 创建新知识库</li>
 * </ul>
 *
 * <h3>字段约束</h3>
 * <ul>
 *   <li>{@code name}：必填，知识库名称，需保证唯一性，建议长度 1~128 字符</li>
 *   <li>{@code description}：选填，知识库描述信息，用于前端展示与检索辅助</li>
 *   <li>{@code status}：选填，知识库初始状态；未传时由服务端默认设置为启用状态</li>
 * </ul>
 *
 * <p>注意：该 Record 由 Java 编译器自动生成构造器、访问器、{@code equals}、
 * {@code hashCode} 及 {@code toString} 方法，无需手动编写。
 *
 * @param name        知识库名称（用于前端展示与检索标识）
 * @param description 知识库描述（补充说明信息，可为空）
 * @param status      知识库状态（枚举值，控制知识库的启用/停用等生命周期）
 * @author Spike
 * @since 1.0.0
 */
public record CreateKnowledgeBaseRequest(
        String name,
        String description,
        KnowledgeBaseStatus status) {
}
