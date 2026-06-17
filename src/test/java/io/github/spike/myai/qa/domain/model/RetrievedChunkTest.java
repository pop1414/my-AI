package io.github.spike.myai.qa.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RetrievedChunk record 单元测试。
 */
class RetrievedChunkTest {

    @Test
    @DisplayName("简化构造器应将 score 默认设为 0.0")
    void constructor_shouldDefaultScoreToZero_whenUsingSimplifiedConstructor() {
        RetrievedChunk chunk = new RetrievedChunk("doc-1", "kb-1", 0, "content");

        assertEquals(0.0, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("全参构造器应正确设置 score 值")
    void constructor_shouldSetScore_whenUsingFullConstructor() {
        RetrievedChunk chunk = new RetrievedChunk(
                "doc-1", "kb-1", 0, "content", 1, "file.pdf", Instant.parse("2026-05-08T10:00:00Z"), 0.85);

        assertEquals(0.85, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("全参构造器应接受 score 为 -1.0 表示余弦反面向量")
    void constructor_shouldAcceptNegativeScore_whenCosineOppositeVector() {
        RetrievedChunk chunk = new RetrievedChunk(
                "doc-1", "kb-1", 0, "content", 1, "file.pdf", Instant.parse("2026-05-08T10:00:00Z"), -1.0);

        assertEquals(-1.0, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("全参构造器应接受 score 为 1.0 表示完全匹配")
    void constructor_shouldAcceptMaxScore_whenPerfectMatch() {
        RetrievedChunk chunk = new RetrievedChunk(
                "doc-1", "kb-1", 0, "content", 1, "file.pdf", Instant.parse("2026-05-08T10:00:00Z"), 1.0);

        assertEquals(1.0, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("简化构造器应正确填充文档身份与内容字段")
    void simplifiedConstructor_shouldPopulateIdentityAndContentFields() {
        RetrievedChunk chunk = new RetrievedChunk("doc-2", "kb-2", 3, "text");

        assertEquals("doc-2", chunk.documentId());
        assertEquals("kb-2", chunk.kbId());
        assertEquals(3, chunk.chunkIndex());
        assertEquals("text", chunk.content());
    }

    @Test
    @DisplayName("简化构造器应将版本元数据字段默认为 null")
    void simplifiedConstructor_shouldDefaultVersionMetadataToNull() {
        RetrievedChunk chunk = new RetrievedChunk("doc-2", "kb-2", 3, "text");

        assertNull(chunk.sourceVersionNumber());
        assertNull(chunk.sourceFilename());
        assertNull(chunk.sourceUpdatedAt());
    }
}
