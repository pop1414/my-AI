package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.time.Instant;

/**
 * 文档版本正文 REST 响应 DTO。
 *
 * <p>该 DTO 用于 latest、askable baseline 和显式版本正文三类来源，
 * 字段语义必须与 OpenAPI 的 {@code DocumentContentResponse} 保持一致。
 *
 * @param documentId          文档资产 ID
 * @param versionNumber       本次返回正文对应的版本号
 * @param latestVersionNumber 当前 document 的最新版本号
 * @param isLatestVersion     返回版本是否为当前 latest
 * @param isAskableVersion    返回版本是否为当前可问答基线版本
 * @param source              正文来源语义
 * @param status              返回版本处理状态
 * @param filename            返回版本来源文件名
 * @param createdAt           返回版本创建时间
 * @param updatedAt           返回版本更新时间
 * @param contentMarkdown     Markdown 正文内容
 * @param contentLength       正文 UTF-8 字节长度
 * @param truncated           是否被截断；首期固定为 false
 */
public record DocumentContentResponse(
        String documentId,
        int versionNumber,
        int latestVersionNumber,
        boolean isLatestVersion,
        boolean isAskableVersion,
        String source,
        String status,
        String filename,
        Instant createdAt,
        Instant updatedAt,
        String contentMarkdown,
        long contentLength,
        boolean truncated) {
}
