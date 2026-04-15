package io.github.spike.myai.qa.application.result;

/**
 * 问答引用分块结果（应用层模型，chunk 级）。
 *
 * <p>该对象用于承接问答流程中的“可解释性信息”，
 * 后续由接口层映射为对外响应 DTO。
 *
 * @param documentId 文档资产 ID
 * @param chunkIndex 分块序号
 * @param contentPreview 分块预览内容（通常经过长度截断）
 */
public record AskReferenceResult(String documentId, int chunkIndex, String contentPreview) {
}
