package io.github.spike.myai.qa.interfaces.rest.dto;

import java.util.List;

/**
 * 问答响应的陈旧引用顶层汇总。
 *
 * @param hasStaleReferences 是否存在非最新版本引用
 * @param staleReferenceCount 陈旧引用条数
 * @param staleDocumentCount 涉及陈旧引用的文档数
 * @param documents 陈旧引用文档明细
 */
public record AskStaleReferenceSummaryResponse(
        boolean hasStaleReferences,
        int staleReferenceCount,
        int staleDocumentCount,
        List<AskStaleReferenceDocumentResponse> documents) {
}
