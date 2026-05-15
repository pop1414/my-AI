package io.github.spike.myai.ingest.application.query;

/**
 * 查询文档正文的应用层查询对象。
 *
 * <p>该查询表达 document 维度和正文来源维度的读取意图。版本选择规则由应用服务
 * 根据 {@link DocumentContentSource} 决定；仅 {@code EXPLICIT_VERSION}
 * 允许调用方指定目标历史版本号。
 *
 * @param documentId    文档资产 ID
 * @param source        正文读取来源
 * @param versionNumber 显式版本读取时的目标版本号
 */
public record GetDocumentContentQuery(String documentId, DocumentContentSource source, Integer versionNumber) {

    /**
     * 构造 latest 或 askable baseline 正文读取查询。
     *
     * @param documentId 文档资产 ID
     * @param source     正文读取来源
     */
    public GetDocumentContentQuery(String documentId, DocumentContentSource source) {
        this(documentId, source, null);
    }

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
        if (source == DocumentContentSource.EXPLICIT_VERSION) {
            if (versionNumber == null) {
                throw new IllegalArgumentException("versionNumber is required when source is EXPLICIT_VERSION");
            }
            if (versionNumber <= 0) {
                throw new IllegalArgumentException("versionNumber must be positive");
            }
        } else if (versionNumber != null) {
            throw new IllegalArgumentException("versionNumber is only allowed when source is EXPLICIT_VERSION");
        }
    }
}
