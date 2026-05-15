package io.github.spike.myai.qa.application.result;

import java.util.List;

/**
 * 问答响应的陈旧引用顶层汇总。
 *
 * <p>该结果只在存在引用时有意义。无引用响应应返回 {@code null}，
 * 避免前端在无依据回答上展示版本提示。</p>
 *
 * @param hasStaleReferences 是否存在非最新版本引用
 * @param staleReferenceCount 陈旧引用条数
 * @param staleDocumentCount 涉及陈旧引用的文档数
 * @param documents 陈旧引用文档明细
 */
public record AskStaleReferenceSummaryResult(
        boolean hasStaleReferences,
        int staleReferenceCount,
        int staleDocumentCount,
        List<AskStaleReferenceDocumentResult> documents) {
}
