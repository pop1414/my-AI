package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.time.Instant;

/**
 * 文档版本历史单项 REST 响应 DTO。
 *
 * <p>该 Record 直接暴露给 API 消费者（前端），
 * 由 Jackson 自动序列化为 JSON 对象。
 * 字段命名遵循 camelCase，与前端 TypeScript 接口约定保持一致。
 *
 * @param documentId               文档资产 ID
 * @param versionNumber            版本号（从 1 开始递增）
 * @param versionOriginType        版本来源类型（UPLOAD / ROLLBACK）
 * @param rollbackFromVersionNumber 回滚来源版本号，非回滚版本时为 null
 * @param filename                 该版本对应的文件名
 * @param fileSize                 文件大小（字节）
 * @param status                   处理状态（INDEXED / FAILED / PROCESSING 等）
 * @param failureReason            失败原因，仅 status=FAILED 时有值
 * @param createdAt                版本创建时间（ISO 8601 格式）
 * @param updatedAt                版本最后更新时间（ISO 8601 格式）
 * @param isLatestVersion          是否为当前最新版本（前端据此高亮显示）
 * @param isAskableVersion         是否为可问答版本（前端据此决定是否开放问答入口）
 */
public record DocumentVersionHistoryItemResponse(
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
