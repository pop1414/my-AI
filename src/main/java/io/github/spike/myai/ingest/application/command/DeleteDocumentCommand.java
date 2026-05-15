package io.github.spike.myai.ingest.application.command;

/**
 * 触发文档资产删除命令。
 *
 * @param documentId 文档资产 ID
 * @param expectedLatestVersionNumber 调用方页面看到的最新版本号，可为空表示兼容旧调用方
 */
public record DeleteDocumentCommand(String documentId, Integer expectedLatestVersionNumber) {

    /**
     * 兼容旧调用方的构造器。
     *
     * @param documentId 文档资产 ID
     */
    public DeleteDocumentCommand(String documentId) {
        this(documentId, null);
    }

    public DeleteDocumentCommand {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        documentId = documentId.trim();
        if (expectedLatestVersionNumber != null && expectedLatestVersionNumber < 1) {
            throw new IllegalArgumentException("expectedLatestVersionNumber must be positive");
        }
    }
}
