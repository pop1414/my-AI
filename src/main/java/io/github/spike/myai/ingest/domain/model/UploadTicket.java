package io.github.spike.myai.ingest.domain.model;

/**
 * 上传受理票据（领域返回模型）。
 *
 * <p>用途：
 * <ul>
 *     <li>表达“系统已受理上传请求”的业务结果。</li>
 *     <li>承载领域关键信息：文档标识与当前状态。</li>
 * </ul>
 *
 * @param documentId 文档唯一标识
 * @param status 当前受理状态
 */
public record UploadTicket(DocumentId documentId, UploadStatus status) {
}
