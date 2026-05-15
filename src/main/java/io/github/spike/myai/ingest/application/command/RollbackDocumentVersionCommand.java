package io.github.spike.myai.ingest.application.command;

/**
 * 文档版本回退命令。
 *
 * <p>该命令绑定一个稳定 document、一个目标历史版本，以及调用方页面看到的最新版本号。
 * 应用层据此完成目标版本校验与 latest projection 的乐观并发校验。
 *
 * @param documentId                  文档资产 ID
 * @param targetVersionNumber         要回退到的历史版本号
 * @param expectedLatestVersionNumber 调用方页面看到的最新版本号
 */
public record RollbackDocumentVersionCommand(
        String documentId,
        int targetVersionNumber,
        int expectedLatestVersionNumber) {

    /**
     * 构造命令并进行基础参数校验。
     */
    public RollbackDocumentVersionCommand {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        documentId = documentId.trim();
        if (targetVersionNumber < 1) {
            throw new IllegalArgumentException("targetVersionNumber must be positive");
        }
        if (expectedLatestVersionNumber < 1) {
            throw new IllegalArgumentException("expectedLatestVersionNumber must be positive");
        }
    }
}
