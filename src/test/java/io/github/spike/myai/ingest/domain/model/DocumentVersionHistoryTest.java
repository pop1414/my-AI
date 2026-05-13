package io.github.spike.myai.ingest.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentVersionHistoryTest {

    @Test
    @DisplayName("版本历史应按版本号倒序暴露并推导当前可问答版本")
    void shouldSortItemsAndResolveAskableVersion() {
        DocumentId documentId = new DocumentId("doc-1");
        DocumentVersionHistory history = new DocumentVersionHistory(
                documentId,
                List.of(
                        item(documentId, 1, UploadStatus.INDEXED),
                        item(documentId, 3, UploadStatus.FAILED),
                        item(documentId, 2, UploadStatus.INDEXED)));

        assertEquals(List.of(3, 2, 1), history.items().stream()
                .map(DocumentVersionHistoryItem::versionNumber)
                .toList());
        assertEquals(2, history.askableVersionNumber());
        assertTrue(history.isLatestVersion(history.items().get(0)));
        assertFalse(history.isAskableVersion(history.items().get(0)));
        assertTrue(history.isAskableVersion(history.items().get(1)));
        assertFalse(history.isAskableVersion(history.items().get(2)));
    }

    @Test
    @DisplayName("没有已索引版本时，应返回 0 作为无可问答版本")
    void shouldReturnZeroAskableVersion_whenNoIndexedVersionExists() {
        DocumentId documentId = new DocumentId("doc-2");
        DocumentVersionHistory history = new DocumentVersionHistory(
                documentId,
                List.of(item(documentId, 1, UploadStatus.FAILED)));

        assertEquals(0, history.askableVersionNumber());
        assertFalse(history.isAskableVersion(history.items().get(0)));
    }

    @Test
    @DisplayName("版本历史项必须属于同一个 document")
    void shouldRejectItemsFromDifferentDocument() {
        DocumentId documentId = new DocumentId("doc-3");

        assertThrows(IllegalArgumentException.class, () -> new DocumentVersionHistory(
                documentId,
                List.of(item(new DocumentId("other-doc"), 1, UploadStatus.INDEXED))));
    }

    private static DocumentVersionHistoryItem item(DocumentId documentId, int versionNumber, UploadStatus status) {
        return new DocumentVersionHistoryItem(
                documentId,
                "workspace-a",
                "kb-1",
                3,
                versionNumber,
                DocumentVersionOriginType.UPLOAD,
                null,
                "version-" + versionNumber + ".pdf",
                versionNumber * 100L,
                status,
                status == UploadStatus.FAILED ? "parse failed" : null,
                Instant.parse("2026-05-08T0" + versionNumber + ":00:00Z"),
                Instant.parse("2026-05-08T0" + versionNumber + ":05:00Z"));
    }
}
