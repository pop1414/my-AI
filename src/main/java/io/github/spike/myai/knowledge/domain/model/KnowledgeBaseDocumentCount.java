package io.github.spike.myai.knowledge.domain.model;

/**
 * 知识库文档计数的领域模型（查询聚合结果）。
 *
 * <p>该记录类型承载“按知识库聚合后的文档数量”，
 * 通常由持久化层执行分组统计后返回给领域/应用层使用。
 *
 * @param kbId 知识库唯一标识
 * @param indexedDocumentCount 已索引文档数量，统计口径为 status = INDEXED
 */
public record KnowledgeBaseDocumentCount(String kbId, long indexedDocumentCount) {
}
