package io.github.spike.myai.knowledge.interfaces.rest.dto;

/**
 * 知识库列表接口响应项（REST DTO）。
 *
 * <p>该对象用于定义对外 API 返回结构，
 * 与内部应用层结果对象分离，避免内部模型变更直接影响接口契约。
 *
 * @param id 知识库唯一标识
 * @param name 知识库名称（用于前端展示）
 * @param indexedDocumentCount 已索引文档数量
 */
public record KnowledgeBaseResponse(String id, String name, long indexedDocumentCount) {
}
