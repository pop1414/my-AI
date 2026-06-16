package io.github.spike.myai.ingest.infrastructure.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.SourceHint;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PgVectorDocumentVectorIndexer 单元测试。
 */
/**
 * TODO(spike): Refactor to integration test
 *
 * Current implementation violates project rule:
 * "Do not mock JdbcTemplate/JDBC chain - SQL correctness must be verified via real database"
 *
 * Refactoring plan:
 * 1. Use Testcontainers for real PostgreSQL environment
 * 2. Remove JdbcTemplate mocks
 * 3. Verify SQL correctness via real database
 *
 * @see docs/project-context.md:187-188
 */
@Disabled("TODO: Refactor to integration test - remove JdbcTemplate mock")
class PgVectorDocumentVectorIndexerTest {

    @Test
    @DisplayName("index 生成的 chunkId 应是可解析的稳定 UUID")
    void index_shouldGenerateDeterministicUuidChunkIds() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        PgVectorDocumentVectorIndexer indexer = new PgVectorDocumentVectorIndexer(vectorStore, jdbcTemplate);

        Document document = new Document(
                new DocumentId("7c01e0fd-a83c-4e4e-8334-722708c72b62"),
                "kb-1",
                "hash-1",
                "a.txt",
                123L,
                UploadStatus.INGESTING,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                null,
                Instant.now(),
                Instant.now());
        List<DocumentChunk> chunks = List.of(
                new DocumentChunk("chunk-a", SourceHint.none()),
                new DocumentChunk("chunk-b", SourceHint.none()));

        indexer.index(document, chunks);
        indexer.index(document, chunks);

        ArgumentCaptor<List<String>> deleteIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).delete(deleteIdsCaptor.capture());
        verify(vectorStore, times(2)).add(Mockito.anyList());

        List<List<String>> allDeleteIds = deleteIdsCaptor.getAllValues();
        List<String> firstRound = allDeleteIds.get(0);
        List<String> secondRound = allDeleteIds.get(1);

        assertEquals(firstRound, secondRound, "同一文档重复写入时，chunkId 应保持稳定");
        for (String chunkId : firstRound) {
            assertDoesNotThrow(() -> UUID.fromString(chunkId), "chunkId 必须是合法 UUID");
        }
    }

    @Test
    @DisplayName("deleteByDocumentId 应删除文档全部版本向量")
    void deleteByDocumentId_shouldDeleteAllVersions() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        PgVectorDocumentVectorIndexer indexer = new PgVectorDocumentVectorIndexer(vectorStore, jdbcTemplate);

        indexer.deleteByDocumentId(new DocumentId("doc-del-vector"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).update(sqlCaptor.capture(), eq("doc-del-vector"));
        assertTrue(sqlCaptor.getValue().contains("metadata->>'documentId' = ?"));
    }

    @Test
    @DisplayName("index 应通过 SourceHint 写入稳定的 metadata 字符串")
    void index_shouldStoreSourceHintMetadataFromValueObject() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        PgVectorDocumentVectorIndexer indexer = new PgVectorDocumentVectorIndexer(vectorStore, jdbcTemplate);
        Instant updatedAt = Instant.parse("2026-05-09T10:00:00Z");
        Document document = new Document(
                new DocumentId("7c01e0fd-a83c-4e4e-8334-722708c72b62"),
                "kb-1",
                "hash-1",
                "a.txt",
                123L,
                UploadStatus.INGESTING,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                null,
                Instant.now(),
                updatedAt);
        List<DocumentChunk> chunks = List.of(new DocumentChunk("chunk-a", SourceHint.heading("第1章 \"结构\"")));

        indexer.index(document, chunks);

        ArgumentCaptor<List<org.springframework.ai.document.Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Map<String, Object> metadata = documentsCaptor.getValue().getFirst().getMetadata();
        assertEquals("{\"heading\":\"第1章 \\\"结构\\\"\"}", metadata.get("sourceHint"));
        assertEquals("default", metadata.get("workspaceId"));
        assertEquals(1, metadata.get("documentVersionNumber"));
        assertEquals("a.txt", metadata.get("sourceFile"));
        assertEquals("2026-05-09T10:00:00Z", metadata.get("sourceUpdatedAt"));
    }
}
