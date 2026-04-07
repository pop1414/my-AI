package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档分块预览入参。
 *
 * @param documentId 文档资产 ID
 * @param limit 最大返回条数（1~200）
 * @param previewChars 预览字符数（20~2000）
 */
public record GetDocumentChunksPreviewQuery(String documentId, int limit, int previewChars) {

    public GetDocumentChunksPreviewQuery {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        if (previewChars < 20 || previewChars > 2000) {
            throw new IllegalArgumentException("previewChars must be between 20 and 2000");
        }
    }
}

