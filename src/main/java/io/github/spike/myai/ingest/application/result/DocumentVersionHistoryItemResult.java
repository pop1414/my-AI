package io.github.spike.myai.ingest.application.result;

import java.time.Instant;

/**
 * 文档版本历史单项应用层返回结果。
 *
 * <p>该 Record 是应用层向 Interface 层传递的只读数据传输对象，
 * 承载单条版本历史记录的完整信息，包括版本号、来源类型、
 * 文件元数据、处理状态以及是否为最新/可问答版本等标记。
 *
 * @param documentId               文档资产 ID
 * @param versionNumber            版本号，从 1 开始递增
 * @param versionOriginType        版本来源类型（如 UPLOAD / ROLLBACK）
 * @param rollbackFromVersionNumber 回滚来源版本号，非回滚版本时为 null
 * @param filename                 文件名
 * @param fileSize                 文件大小（字节）
 * @param status                   处理状态（如 INDEXED / FAILED / PROCESSING）
 * @param failureReason            失败原因，仅当 status 为 FAILED 时有值，否则为 null
 * @param createdAt                创建时间
 * @param updatedAt                最后更新时间
 * @param isLatestVersion          是否为当前最新版本
 * @param isAskableVersion         是否为当前问答基线使用的版本；最新版本未 INDEXED 时回退到最近一个 INDEXED 版本
 */
public record DocumentVersionHistoryItemResult(
        String documentId,
        int versionNumber,
        String versionOriginType,
        Integer rollbackFromVersionNumber,
        String filename,
        long fileSize,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        boolean isLatestVersion,
        boolean isAskableVersion) {
}
