package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档分块预览入参。
 *
 * @param documentId 文档资产 ID
 * @param limit 最大返回条数（1~200）
 * @param offset 起始偏移（>=0）
 * @param previewChars 预览字符数（20~2000）
 */
public record GetDocumentChunksPreviewQuery(String documentId, int limit, int offset, int previewChars) {

    public GetDocumentChunksPreviewQuery {
        // 统一在应用层做参数范围校验，防止异常输入压垮后端。
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        if (offset < 0 || offset > 100000) {
            throw new IllegalArgumentException("offset must be between 0 and 100000");
        }
        if (previewChars < 20 || previewChars > 2000) {
            throw new IllegalArgumentException("previewChars must be between 20 and 2000");
        }
    }
}
