package io.github.spike.myai.ingest.infrastructure.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.chunk.response.Document;
import ai.docling.serve.api.chunk.response.ExportDocumentResponse;
import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * DoclingDocumentChunker 单元测试。
 */
class DoclingDocumentChunkerTest {

    private DoclingServeApi doclingServeApi;
    private DoclingDocumentChunker chunker;

    @BeforeEach
    void setUp() {
        doclingServeApi = org.mockito.Mockito.mock(DoclingServeApi.class);
        IngestProperties properties = new IngestProperties();
        chunker = new DoclingDocumentChunker(doclingServeApi, properties);
    }

    // === 正常分块 ===

    @Test
    @DisplayName("应将 Docling 响应 chunks 映射为 DocumentChunk 列表")
    void chunk_shouldMapChunksToDocumentChunks() {
        ChunkDocumentResponse response = buildResponse(List.of(
                buildChunk("第一段内容", List.of("标题一"), List.of(1), 0),
                buildChunk("第二段内容", List.of("标题一", "子标题"), List.of(2), 1)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        List<DocumentChunk> chunks = chunker.chunk("# 标题一\n\n第一段内容\n\n## 子标题\n\n第二段内容");

        assertEquals(2, chunks.size());
        assertEquals("第一段内容", chunks.get(0).content());
        assertEquals("第二段内容", chunks.get(1).content());
    }

    @Test
    @DisplayName("应正确映射 ChunkMetadata（headings + pageNumber + contentType）")
    void chunk_shouldMapChunkMetadataCorrectly() {
        ChunkDocumentResponse response = buildResponse(List.of(
                buildChunk("内容", List.of("第一章", "第一节"), List.of(5), 0)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        List<DocumentChunk> chunks = chunker.chunk("markdown");

        ChunkMetadata metadata = chunks.getFirst().chunkMetadata();
        assertEquals(List.of("第一章", "第一节"), metadata.headings());
        assertEquals(5, metadata.pageNumber());
        assertEquals(ChunkContentType.PARAGRAPH, metadata.contentType());
    }

    @Test
    @DisplayName("headings 为空时应归一化为空 list，pageNumber 默认 0")
    void chunk_shouldNormalizeEmptyMetadata() {
        ChunkDocumentResponse response = buildResponse(List.of(
                buildChunk("纯文本内容", null, null, 0)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        List<DocumentChunk> chunks = chunker.chunk("纯文本内容");

        ChunkMetadata metadata = chunks.getFirst().chunkMetadata();
        assertTrue(metadata.headings().isEmpty());
        assertEquals(0, metadata.pageNumber());
        assertEquals(ChunkContentType.PARAGRAPH, metadata.contentType());
    }

    // === 请求参数验证 ===

    @Test
    @DisplayName("应正确构造 HybridChunkDocumentRequest（to_formats=[MD], includeConvertedDoc=false）")
    void chunk_shouldBuildCorrectRequest() {
        ChunkDocumentResponse response = buildResponse(List.of(
                buildChunk("内容", List.of(), List.of(), 0)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        chunker.chunk("test markdown");

        ArgumentCaptor<HybridChunkDocumentRequest> captor =
                ArgumentCaptor.forClass(HybridChunkDocumentRequest.class);
        verify(doclingServeApi).chunkSourceWithHybridChunker(captor.capture());

        HybridChunkDocumentRequest request = captor.getValue();
        assertEquals(1, request.getSources().size());
        assertEquals(false, request.isIncludeConvertedDoc());
        assertNotNull(request.getChunkingOptions());
        assertEquals(Integer.valueOf(512), request.getChunkingOptions().getMaxTokens());
        assertEquals(Boolean.TRUE, request.getChunkingOptions().getMergePeers());
    }

    @Test
    @DisplayName("应从 IngestProperties 读取 maxTokens 和 mergePeers 配置")
    void chunk_shouldUseConfiguredChunkingParams() {
        IngestProperties customProps = new IngestProperties();
        customProps.getChunk().setMaxTokens(256);
        customProps.getChunk().setMergePeers(false);
        DoclingDocumentChunker customChunker = new DoclingDocumentChunker(doclingServeApi, customProps);

        ChunkDocumentResponse response = buildResponse(List.of(
                buildChunk("内容", List.of(), List.of(), 0)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        customChunker.chunk("markdown");

        ArgumentCaptor<HybridChunkDocumentRequest> captor =
                ArgumentCaptor.forClass(HybridChunkDocumentRequest.class);
        verify(doclingServeApi).chunkSourceWithHybridChunker(captor.capture());

        assertEquals(Integer.valueOf(256), captor.getValue().getChunkingOptions().getMaxTokens());
        assertEquals(Boolean.FALSE, captor.getValue().getChunkingOptions().getMergePeers());
    }

    // === 异常处理 ===

    @Test
    @DisplayName("Docling 4xx 错误（非 408/429）应抛出 DoclingPermanentException")
    void chunk_shouldThrowPermanentException_on4xxError() {
        HttpClientErrorException httpError = HttpClientErrorException.create(
                HttpStatusCode.valueOf(400), "Bad Request", null, null, null);
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(httpError);

        assertThrows(DoclingPermanentException.class, () -> chunker.chunk("markdown"));
    }

    @Test
    @DisplayName("Docling 408 错误应抛出 DoclingTransientException")
    void chunk_shouldThrowTransientException_on408Error() {
        HttpClientErrorException httpError = HttpClientErrorException.create(
                HttpStatusCode.valueOf(408), "Request Timeout", null, null, null);
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(httpError);

        assertThrows(DoclingTransientException.class, () -> chunker.chunk("markdown"));
    }

    @Test
    @DisplayName("Docling 429 错误应抛出 DoclingTransientException")
    void chunk_shouldThrowTransientException_on429Error() {
        HttpClientErrorException httpError = HttpClientErrorException.create(
                HttpStatusCode.valueOf(429), "Too Many Requests", null, null, null);
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(httpError);

        assertThrows(DoclingTransientException.class, () -> chunker.chunk("markdown"));
    }

    @Test
    @DisplayName("Docling 5xx 错误应抛出 DoclingTransientException")
    void chunk_shouldThrowTransientException_on5xxError() {
        HttpServerErrorException httpError = HttpServerErrorException.create(
                HttpStatusCode.valueOf(500), "Internal Server Error", null, null, null);
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(httpError);

        assertThrows(DoclingTransientException.class, () -> chunker.chunk("markdown"));
    }

    @Test
    @DisplayName("网络超时应抛出 DoclingTransientException")
    void chunk_shouldThrowTransientException_onTimeout() {
        ResourceAccessException timeoutError = new ResourceAccessException("connection timed out");
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(timeoutError);

        assertThrows(DoclingTransientException.class, () -> chunker.chunk("markdown"));
    }

    @Test
    @DisplayName("未知异常应抛出 DoclingTransientException（保守策略）")
    void chunk_shouldThrowTransientException_onUnknownError() {
        RuntimeException unknownError = new RuntimeException("unexpected");
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(unknownError);

        assertThrows(DoclingTransientException.class, () -> chunker.chunk("markdown"));
    }

    // === 边界条件 ===

    @Test
    @DisplayName("Docling 响应无 chunks 时应抛出 IllegalStateException")
    void chunk_shouldThrowWhenNoChunks() {
        ChunkDocumentResponse response = ChunkDocumentResponse.builder()
                .documents(List.of())
                .chunks(List.of())
                .build();
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        assertThrows(IllegalStateException.class, () -> chunker.chunk("markdown"));
    }

    // === Helpers ===

    private ChunkDocumentResponse buildResponse(List<Chunk> chunks) {
        Document doc = Document.builder()
                .content(ExportDocumentResponse.builder().markdownContent("content").build())
                .status("success")
                .build();
        return ChunkDocumentResponse.builder()
                .documents(List.of(doc))
                .chunks(chunks)
                .build();
    }

    private Chunk buildChunk(String text, List<String> headings, List<Integer> pageNumbers, int chunkIndex) {
        Chunk.Builder builder = Chunk.builder()
                .text(text)
                .chunkIndex(chunkIndex)
                .filename("input.md");
        if (headings != null) {
            builder.headings(headings);
        }
        if (pageNumbers != null) {
            builder.pageNumbers(pageNumbers);
        }
        return builder.build();
    }
}
