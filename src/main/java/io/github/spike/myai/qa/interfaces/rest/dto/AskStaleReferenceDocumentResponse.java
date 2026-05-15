package io.github.spike.myai.qa.interfaces.rest.dto;

/**
 * 陈旧引用文档响应项。
 *
 * @param documentId 文档资产 ID
 * @param sourceVersionNumber 实际引用的版本号
 * @param latestVersionNumber 当前最新版本号
 * @param sourceFilename 实际引用版本的来源文件名
 */
public record AskStaleReferenceDocumentResponse(
        String documentId,
        int sourceVersionNumber,
        int latestVersionNumber,
        String sourceFilename) {
}
