package io.github.spike.myai.qa.application.result;

import java.time.Instant;

/**
 * 问答引用分块结果（应用层模型，chunk 级）。
 *
 * <p>该对象用于承接问答流程中的“可解释性信息”，
 * 后续由接口层映射为对外响应 DTO。
 *
 * @param documentId 文档资产 ID
 * @param chunkIndex 分块序号
 * @param contentPreview 分块预览内容（通常经过长度截断）
 * @param sourceVersionNumber 引用来源版本号
 * @param sourceUpdatedAt 引用来源版本更新时间
 * @param isLatestVersion 引用来源是否为当前最新版本
 * @param latestVersionNumber 当前最新版本号
 * @param sourceFilename 引用来源文件名
 */
public record AskReferenceResult(
        String documentId,
        int chunkIndex,
        String contentPreview,
        int sourceVersionNumber,
        Instant sourceUpdatedAt,
        boolean isLatestVersion,
        int latestVersionNumber,
        String sourceFilename) {

    /**
     * 兼容旧调用方的简化构造器。
     *
     * @param documentId 文档资产 ID
     * @param chunkIndex 分块序号
     * @param contentPreview 分块预览内容
     */
    public AskReferenceResult(String documentId, int chunkIndex, String contentPreview) {
        this(documentId, chunkIndex, contentPreview, 1, null, true, 1, null);
    }
}
