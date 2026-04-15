package io.github.spike.myai.qa.domain.model;

/**
 * 向量检索命中的分块快照（领域查询模型）。
 *
 * <p>该对象表示检索层返回的最小必要信息，供应用层进行知识库过滤、
 * 引用组装与提示词拼接，不绑定具体向量数据库实现细节。
 *
 * @param documentId 文档资产 ID
 * @param kbId 知识库 ID
 * @param chunkIndex 分块序号
 * @param content 分块正文
 */
public record RetrievedChunk(String documentId, String kbId, int chunkIndex, String content) {
}
