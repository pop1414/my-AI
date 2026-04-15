package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * PgVectorChunkRetrievalAdapter 单元测试。
 */
class PgVectorChunkRetrievalAdapterTest {

    @Test
    @DisplayName("similaritySearch 应按请求参数检索并映射 chunk 元数据")
    void similaritySearch_shouldMapRetrievedChunks() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        PgVectorChunkRetrievalAdapter adapter = new PgVectorChunkRetrievalAdapter(vectorStore);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                org.springframework.ai.document.Document.builder()
                        .id("chunk-1")
                        .text("hello")
                        .metadata(Map.of("documentId", "doc-1", "kbId", "default", "chunkIndex", 3))
                        .build()));

        var result = adapter.similaritySearch("what is rag", 7);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(1)).similaritySearch(requestCaptor.capture());
        assertEquals("what is rag", requestCaptor.getValue().getQuery());
        assertEquals(7, requestCaptor.getValue().getTopK());

        assertEquals(1, result.size());
        assertEquals("doc-1", result.get(0).documentId());
        assertEquals("default", result.get(0).kbId());
        assertEquals(3, result.get(0).chunkIndex());
        assertEquals("hello", result.get(0).content());
    }
}
