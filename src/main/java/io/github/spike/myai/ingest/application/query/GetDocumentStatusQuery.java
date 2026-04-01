package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档状态用例的输入参数（Query DTO）。
 *
 * @param documentId 文档 ID（来自路径参数）
 */
public record GetDocumentStatusQuery(String documentId) {
}
