package io.github.spike.myai.qa.application.result;

/**
 * 陈旧引用文档汇总项。
 *
 * <p>当问答引用回退到非最新版本时，该对象用于描述具体是哪一个文档的哪一个版本
 * 被用于回答，便于接口层向前端提供版本提示。</p>
 *
 * @param documentId 文档资产 ID
 * @param sourceVersionNumber 实际引用的可问答版本号
 * @param latestVersionNumber 当前最新版本号
 * @param sourceFilename 实际引用版本的来源文件名
 */
public record AskStaleReferenceDocumentResult(
        String documentId,
        int sourceVersionNumber,
        int latestVersionNumber,
        String sourceFilename) {
}
