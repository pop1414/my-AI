package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档正文的应用层查询对象。
 *
 * <p>该查询表达 document 维度和正文来源维度的读取意图。版本选择规则由应用服务
 * 根据 {@link DocumentContentSource} 决定，调用方不能通过该对象指定历史版本。
 *
 * @param documentId 文档资产 ID
 * @param source     正文读取来源
 */
public record GetDocumentContentQuery(String documentId, DocumentContentSource source) {

    /**
     * 校验正文读取查询参数。
     */
    public GetDocumentContentQuery {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        documentId = documentId.trim();
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
    }
}
