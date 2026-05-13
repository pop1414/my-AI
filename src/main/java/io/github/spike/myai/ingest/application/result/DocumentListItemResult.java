package io.github.spike.myai.ingest.application.result;

import java.time.Instant;

/**
 * 文档列表结果项。
 */
public record DocumentListItemResult(
        String documentId,
        String kbId,
        int latestVersionNumber,
        String latestVersionOriginType,
        String filename,
        long fileSize,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public DocumentListItemResult(
            String documentId,
            String kbId,
            String filename,
            long fileSize,
            String status,
            String failureReason,
            Instant createdAt,
            Instant updatedAt) {
        this(documentId, kbId, 1, "UPLOAD", filename, fileSize, status, failureReason, createdAt, updatedAt);
    }
}
