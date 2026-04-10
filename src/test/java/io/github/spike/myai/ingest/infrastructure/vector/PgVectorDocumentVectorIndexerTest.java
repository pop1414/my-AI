package io.github.spike.myai.ingest.infrastructure.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PgVectorDocumentVectorIndexer 单元测试。
 */
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
                Instant.now(),
                Instant.now());
        List<DocumentChunk> chunks = List.of(new DocumentChunk("chunk-a", null), new DocumentChunk("chunk-b", null));

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
}
