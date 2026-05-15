package io.github.spike.myai.ingest.application.result;

/**
 * 上传新版本结果 DTO。
 *
 * @param documentId 文档资产 ID
 * @param versionCreated 是否创建了新版本
 * @param versionResultType 结果类型
 * @param versionNumber 新创建版本号；复用时为空
 * @param previousVersionNumber 创建新版本前的最新版本号
 * @param reusedLatestVersionNumber 同内容复用时仍停留的最新版本号
 * @param latestVersionNumber 系统当前最新版本号
 * @param askableVersionNumber 当前可问答版本号；没有可问答版本时为空
 * @param canAskNow 当前是否存在可问答版本
 * @param status 当前最新版本状态
 * @param versionOriginType 当前最新版本来源类型
 */
public record DocumentVersionUploadResult(
        String documentId,
        boolean versionCreated,
        String versionResultType,
        Integer versionNumber,
        Integer previousVersionNumber,
        Integer reusedLatestVersionNumber,
        int latestVersionNumber,
        Integer askableVersionNumber,
        boolean canAskNow,
        String status,
        String versionOriginType) {
}
