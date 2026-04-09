package io.github.spike.myai.ingest.application.command;

/**
 * 触发文档重处理命令。
 *
 * @param documentId 文档资产 ID
 */
public record ReprocessDocumentCommand(String documentId) {

    public ReprocessDocumentCommand {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }
}
