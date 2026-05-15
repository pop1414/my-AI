package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;

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
                        .metadata(Map.of(
                                "documentId", "doc-1",
                                "kbId", "default",
                                "chunkIndex", 3,
                                "documentVersionNumber", 2,
                                "sourceFile", "doc-1-v2.pdf",
                                "sourceUpdatedAt", "2026-05-09T10:00:00Z"))
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
        assertEquals(2, result.get(0).sourceVersionNumber());
        assertEquals("doc-1-v2.pdf", result.get(0).sourceFilename());
        assertEquals(java.time.Instant.parse("2026-05-09T10:00:00Z"), result.get(0).sourceUpdatedAt());
    }

    @Test
    @DisplayName("similaritySearch 应从旧向量 splitVersion 兼容解析来源版本号")
    void similaritySearch_shouldResolveSourceVersionFromSplitVersionForLegacyVectors() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        PgVectorChunkRetrievalAdapter adapter = new PgVectorChunkRetrievalAdapter(vectorStore);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                org.springframework.ai.document.Document.builder()
                        .id("chunk-legacy")
                        .text("legacy")
                        .metadata(Map.of(
                                "documentId", "doc-legacy",
                                "kbId", "default",
                                "chunkIndex", 1,
                                "sourceFile", "doc-legacy-v2.pdf",
                                "splitVersion", "version-2-v1"))
                        .build()));

        var result = adapter.similaritySearch("what is rag", 3);

        assertEquals(1, result.size());
        assertEquals("doc-legacy", result.get(0).documentId());
        assertEquals(2, result.get(0).sourceVersionNumber());
        assertEquals("doc-legacy-v2.pdf", result.get(0).sourceFilename());
    }

    @Test
    @DisplayName("similaritySearch 应把可问答版本范围下推为向量过滤表达式")
    void similaritySearch_shouldPushAskableVersionScopeToVectorFilter() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        PgVectorChunkRetrievalAdapter adapter = new PgVectorChunkRetrievalAdapter(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.similaritySearch(
                "what is rag",
                7,
                List.of(
                        new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", Instant.parse("2026-05-09T10:00:00Z")),
                        new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", Instant.parse("2026-05-11T10:00:00Z"))));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertEquals("what is rag", requestCaptor.getValue().getQuery());
        assertEquals(7, requestCaptor.getValue().getTopK());
        org.junit.jupiter.api.Assertions.assertNotNull(requestCaptor.getValue().getFilterExpression());
        String filterExpression = requestCaptor.getValue().getFilterExpression().toString();
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("documentId"));
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("documentVersionNumber"));
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("splitVersion"));
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("version-2-v1"));
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("doc-1"));
        org.junit.jupiter.api.Assertions.assertTrue(filterExpression.contains("doc-2"));
    }

    @Test
    @DisplayName("similaritySearch 构造的版本过滤表达式应兼容 PGVector 转换器")
    void similaritySearch_shouldBuildPgVectorCompatibleFilterExpression() {
        VectorStore vectorStore = Mockito.mock(VectorStore.class);
        PgVectorChunkRetrievalAdapter adapter = new PgVectorChunkRetrievalAdapter(vectorStore);
        PgVectorFilterExpressionConverter converter = new PgVectorFilterExpressionConverter();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            assertDoesNotThrow(() -> converter.convertExpression(request.getFilterExpression()));
            return List.of();
        });

        adapter.similaritySearch(
                "what is rag",
                7,
                List.of(new AskableDocumentVersion(
                        "doc-1",
                        1,
                        1,
                        "doc-1-v1.pdf",
                        Instant.parse("2026-05-09T10:00:00Z"))));

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }
}
