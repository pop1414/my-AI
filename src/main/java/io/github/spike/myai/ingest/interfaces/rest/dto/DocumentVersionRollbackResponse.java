package io.github.spike.myai.ingest.interfaces.rest.dto;

/**
 * 文档版本回退响应。
 *
 * @param documentId                文档资产 ID
 * @param versionNumber             回退创建的新版本号
 * @param rollbackFromVersionNumber 回退来源历史版本号
 * @param latestVersionNumber       系统当前最新版本号
 * @param askableVersionNumber      当前可问答版本号，不存在时为 null
 * @param canAskNow                 当前是否存在可问答版本
 * @param status                    当前最新版本状态
 * @param versionOriginType         当前最新版本来源类型
 */
public record DocumentVersionRollbackResponse(
        String documentId,
        int versionNumber,
        int rollbackFromVersionNumber,
        int latestVersionNumber,
        Integer askableVersionNumber,
        boolean canAskNow,
        String status,
        String versionOriginType) {
}
