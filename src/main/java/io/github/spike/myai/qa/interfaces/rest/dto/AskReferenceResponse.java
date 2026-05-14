package io.github.spike.myai.qa.interfaces.rest.dto;

import java.time.Instant;

/**
 * 问答引用分块响应项。
 *
 * <p>用于让调用方理解回答依据来源，便于前端展示“来源片段”或做可追溯审计。
 *
 * @param documentId 引用片段所属文档 ID
 * @param chunkIndex 引用片段在文档中的分块序号
 * @param contentPreview 引用片段预览文本（可能已被截断）
 * @param sourceVersionNumber 引用来源版本号
 * @param sourceUpdatedAt 引用来源版本更新时间
 * @param isLatestVersion 引用来源是否为当前最新版本
 * @param latestVersionNumber 当前最新版本号
 * @param sourceFilename 引用来源文件名
 */
public record AskReferenceResponse(
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
     * @param documentId 引用片段所属文档 ID
     * @param chunkIndex 引用片段在文档中的分块序号
     * @param contentPreview 引用片段预览文本
     */
    public AskReferenceResponse(String documentId, int chunkIndex, String contentPreview) {
        this(documentId, chunkIndex, contentPreview, 1, null, true, 1, null);
    }
}
