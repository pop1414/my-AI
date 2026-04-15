package io.github.spike.myai.qa.interfaces.rest.dto;

/**
 * 问答引用分块响应项。
 *
 * <p>用于让调用方理解回答依据来源，便于前端展示“来源片段”或做可追溯审计。
 *
 * @param documentId 引用片段所属文档 ID
 * @param chunkIndex 引用片段在文档中的分块序号
 * @param contentPreview 引用片段预览文本（可能已被截断）
 */
public record AskReferenceResponse(String documentId, int chunkIndex, String contentPreview) {
}
