package io.github.spike.myai.ingest.domain.model;

import java.util.Comparator;
import java.util.List;

/**
 * 文档版本历史读模型。
 *
 * <p>该模型承载同一 document 的线性版本链只读视图，并集中维护版本历史排序、
 * latest 标记和当前 QA 基线推导规则。
 *
 * @param documentId 文档资产 ID
 * @param items      版本历史项，按 versionNumber 倒序暴露
 */
public record DocumentVersionHistory(
        DocumentId documentId,
        List<DocumentVersionHistoryItem> items) {

    public DocumentVersionHistory {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (items == null) {
            throw new IllegalArgumentException("items must not be null");
        }
        items.forEach(item -> {
            if (!documentId.equals(item.documentId())) {
                throw new IllegalArgumentException("history item documentId must match history documentId");
            }
        });
        items = items.stream()
                .sorted(Comparator.comparingInt(DocumentVersionHistoryItem::versionNumber).reversed())
                .toList();
    }

    public boolean isLatestVersion(DocumentVersionHistoryItem item) {
        return item.versionNumber() == item.latestVersionNumber();
    }

    public boolean isAskableVersion(DocumentVersionHistoryItem item) {
        return item.versionNumber() == askableVersionNumber();
    }

    public int askableVersionNumber() {
        return items.stream()
                .filter(item -> item.status() == UploadStatus.INDEXED)
                .map(DocumentVersionHistoryItem::versionNumber)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
}
