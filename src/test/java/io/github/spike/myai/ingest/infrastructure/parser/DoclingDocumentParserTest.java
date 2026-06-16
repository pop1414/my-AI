package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ai.docling.serve.api.convert.response.ErrorItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DoclingDocumentParser 单元测试。
 */
class DoclingDocumentParserTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC);

    private DoclingServeApi doclingServeApi;
    private DoclingDocumentParser parser;

    @BeforeEach
    void setUp() {
        doclingServeApi = org.mockito.Mockito.mock(DoclingServeApi.class);
        IngestProperties properties = new IngestProperties();
        parser = new DoclingDocumentParser(doclingServeApi, new ObjectMapper(), properties, FIXED_CLOCK);
    }

    // === 正常解析 ===

    @Test
    @DisplayName("应将 Docling 响应映射为 DocumentParseResult（cleanedMarkdown + processingMetadata）")
    void parse_shouldReturnParseResult_whenValidDocument() throws Exception {
        ChunkDocumentResponse response = buildResponse(
                "# 标题\n\n正文内容",
                List.of(buildChunk("正文内容", List.of("标题"), List.of(1), 0)));
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("test.pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        assertEquals("# 标题\n\n正文内容", result.cleanedMarkdown());

        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("v1", metadata.path("schema_version").asText());
        assertEquals("test.pdf", metadata.path("stable").path("source_file").asText());
        assertEquals("pdf", metadata.path("stable").path("file_ext").asText());
        assertEquals("2026-06-16T12:00:00Z", metadata.path("stable").path("created_at").asText());
    }

    @Test
    @DisplayName("应正确映射 ChunkMetadata（headings + pageNumber + contentType）")
    void parse_shouldMapChunkMetadata_whenChunksReturned() {
        Chunk doclingChunk = buildChunk("正文", List.of("章标题", "节标题"), List.of(3, 5), 0);

        ChunkMetadata metadata = parser.mapChunkMetadata(doclingChunk);

        assertEquals(List.of("章标题", "节标题"), metadata.headings());
        assertEquals(3, metadata.pageNumber());
        assertEquals(ChunkContentType.PARAGRAPH, metadata.contentType());
    }

    @Test
    @DisplayName("应使用 ChunkMetadata.of() 归一化 headings 为 null 的 chunk")
    void parse_shouldUseChunkMetadataOf_whenHeadingsNull() {
        Chunk doclingChunk = buildChunk("纯文本", null, null, 0);

        ChunkMetadata metadata = parser.mapChunkMetadata(doclingChunk);

        assertTrue(metadata.headings().isEmpty());
        assertEquals(0, metadata.pageNumber());
        assertEquals(ChunkContentType.PARAGRAPH, metadata.contentType());
    }

    @Test
    @DisplayName("应正确提取 processingMetadata 中的 title_outline_sample")
    void parse_shouldExtractTitleOutlineSample_whenMarkdownHasHeadings() throws Exception {
        String markdown = "# 主标题\n\n## 节一\n\n正文\n\n## 节二\n\n正文";
        ChunkDocumentResponse response = buildResponse(markdown, List.of());
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("doc.md", "dummy".getBytes(StandardCharsets.UTF_8));

        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("主标题", metadata.path("conditional").path("primary_title").asText());
        assertEquals(3, metadata.path("conditional").path("title_outline_sample").size());
        assertEquals("主标题", metadata.path("conditional").path("title_outline_sample").get(0).asText());
        assertEquals("节一", metadata.path("conditional").path("title_outline_sample").get(1).asText());
        assertEquals("节二", metadata.path("conditional").path("title_outline_sample").get(2).asText());
    }

    // === 输入校验 ===

    @Test
    @DisplayName("空内容应抛出 IllegalStateException")
    void parse_shouldThrowException_whenContentEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("empty.txt", new byte[0]));
        assertEquals("empty source content", ex.getMessage());
    }

    @Test
    @DisplayName("null 内容应抛出 IllegalStateException")
    void parse_shouldThrowException_whenContentNull() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("null.txt", null));
        assertEquals("empty source content", ex.getMessage());
    }

    // === API 异常 ===

    @Test
    @DisplayName("Docling API 异常应包装为 IllegalStateException 并保留 cause")
    void parse_shouldThrowException_whenApiThrowsException() {
        RuntimeException apiError = new RuntimeException("connection refused");
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenThrow(apiError);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("error.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("failed to parse content with docling", ex.getMessage());
        assertEquals(apiError, ex.getCause());
    }

    // === 响应校验 ===

    @Test
    @DisplayName("documents 列表为空应抛出 IllegalStateException")
    void parse_shouldThrowException_whenDocumentsEmpty() {
        ChunkDocumentResponse response = ChunkDocumentResponse.builder()
                .documents(List.of())
                .chunks(List.of())
                .build();
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("empty-doc.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling response contains no documents", ex.getMessage());
    }

    @Test
    @DisplayName("markdownContent 为 null 应抛出 IllegalStateException")
    void parse_shouldThrowException_whenMarkdownContentNull() {
        Document doc = Document.builder()
                .content(ExportDocumentResponse.builder().build())
                .status("success")
                .build();
        ChunkDocumentResponse response = ChunkDocumentResponse.builder()
                .documents(List.of(doc))
                .chunks(List.of())
                .build();
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("null-md.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling response document has no markdown content", ex.getMessage());
    }

    // === 长度校验 ===

    @Test
    @DisplayName("超出最大文本长度时应抛出 IllegalStateException")
    void parse_shouldValidateMaxLength_whenExceedThreshold() {
        IngestProperties shortLimit = new IngestProperties();
        shortLimit.getParser().setMaxTextLength(10);
        DoclingDocumentParser shortParser = new DoclingDocumentParser(
                doclingServeApi, new ObjectMapper(), shortLimit, FIXED_CLOCK);

        String longMarkdown = "a".repeat(20);
        ChunkDocumentResponse response = buildResponse(longMarkdown, List.of());
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> shortParser.parse("long.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("parsed text exceeds max length", ex.getMessage());
    }

    // === 请求参数验证 ===

    @Test
    @DisplayName("应正确构造 HybridChunkDocumentRequest（maxTokens=512, mergePeers=true, includeConvertedDoc=true）")
    void parse_shouldBuildCorrectRequest_whenCalled() throws Exception {
        ChunkDocumentResponse response = buildResponse("内容", List.of());
        when(doclingServeApi.chunkSourceWithHybridChunker(any())).thenReturn(response);

        parser.parse("test.pdf", "hello".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HybridChunkDocumentRequest> captor =
                ArgumentCaptor.forClass(HybridChunkDocumentRequest.class);
        verify(doclingServeApi).chunkSourceWithHybridChunker(captor.capture());

        HybridChunkDocumentRequest request = captor.getValue();
        assertTrue(request.isIncludeConvertedDoc());
        assertEquals(Integer.valueOf(512), request.getChunkingOptions().getMaxTokens());
        assertEquals(Boolean.TRUE, request.getChunkingOptions().getMergePeers());
        assertEquals(1, request.getSources().size());
    }

    // === Helpers ===

    private ChunkDocumentResponse buildResponse(String markdownContent, List<Chunk> chunks) {
        ExportDocumentResponse exportDoc = ExportDocumentResponse.builder()
                .markdownContent(markdownContent)
                .build();
        Document doc = Document.builder()
                .content(exportDoc)
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
                .filename("test.pdf");
        if (headings != null) {
            builder.headings(headings);
        }
        if (pageNumbers != null) {
            builder.pageNumbers(pageNumbers);
        }
        return builder.build();
    }
}
