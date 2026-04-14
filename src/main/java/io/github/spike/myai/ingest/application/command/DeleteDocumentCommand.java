package io.github.spike.myai.ingest.application.command;

/**
 * 触发文档资产删除命令。
 *
 * @param documentId 文档资产 ID
 */
public record DeleteDocumentCommand(String documentId) {

    public DeleteDocumentCommand {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }
}
