package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档 latest 正文的应用层查询对象。
 *
 * <p>该查询只表达 document 维度的默认正文读取意图，版本选择规则由应用服务固定为
 * 当前 latest version，调用方不能通过该对象指定历史版本。
 *
 * @param documentId 文档资产 ID
 */
public record GetDocumentContentQuery(String documentId) {

    /**
     * 校验正文读取查询参数。
     */
    public GetDocumentContentQuery {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        documentId = documentId.trim();
    }
}
