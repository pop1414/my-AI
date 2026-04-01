package io.github.spike.myai.ingest.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Document 聚合根的领域行为测试。
 */
class DocumentTest {

    @Test
    @DisplayName("uploaded 工厂方法应创建 UPLADED 状态的文档")
    void uploaded_shouldCreateDocumentWithUploadedStatus() {
        Instant now = Instant.now();
        Document document = Document.uploaded(new DocumentId("doc-u1"), "kb-1", "hash-u1", "x.txt", 100L, now);

        assertEquals(UploadStatus.UPLOADED, document.status());
        assertEquals("hash-u1", document.fileHash());
        assertEquals(now, document.createdAt());
        assertEquals(now, document.updatedAt());
    }

    @Test
    @DisplayName("markIngesting/markIndexed/markFailed 应返回对应状态的新实例")
    void stateTransitions_shouldReturnNewDocumentWithExpectedStatus() {
        Instant now = Instant.now();
        Document uploaded = Document.uploaded(new DocumentId("doc-u2"), "kb-2", "hash-u2", "y.txt", 200L, now);

        Document ingesting = uploaded.markIngesting(now.plusSeconds(1));
        Document indexed = ingesting.markIndexed(now.plusSeconds(2));
        Document failed = indexed.markFailed("mock failure", now.plusSeconds(3));

        assertEquals(UploadStatus.INGESTING, ingesting.status());
        assertEquals(UploadStatus.INDEXED, indexed.status());
        assertEquals(UploadStatus.FAILED, failed.status());
        assertEquals("mock failure", failed.failureReason());
    }
}
