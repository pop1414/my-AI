package io.github.spike.myai.knowledge.application.result;

/**
 * 知识库列表项结果对象（应用层返回模型）。
 *
 * <p>该对象用于承接用例执行结果，供接口层转换为 HTTP 响应。
 * 将应用层结果与接口层 DTO 分离，可以降低 API 变化对业务用例的影响。
 *
 * @param id 知识库唯一标识（对外业务键 kb_id）
 * @param name 知识库显示名称
 * @param description 知识库描述
 * @param status 知识库状态
 * @param indexedDocumentCount 已索引文档数量
 */
public record KnowledgeBaseResult(
        String id,
        String name,
        String description,
        String status,
        long indexedDocumentCount) {
}
