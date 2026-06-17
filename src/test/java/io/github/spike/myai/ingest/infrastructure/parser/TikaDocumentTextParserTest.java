package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * TikaDocumentTextParser 单元测试。
 *
 * <p>Story 3.1 重构后：DOCLING 路由委托 {@link DoclingDocumentParser}，
 * 测试聚焦于路由委托行为，不再验证 Tika 内部解析逻辑。
 */
class TikaDocumentTextParserTest {

    private TextCleaningService cleaningService;
    private DocumentTextParser doclingDocumentParser;
    private TikaDocumentTextParser parser;

    @BeforeEach
    void setUp() {
        cleaningService = new TextCleaningService();
        doclingDocumentParser = org.mockito.Mockito.mock(DocumentTextParser.class);

        // mock DoclingDocumentParser 返回合理的解析结果
        when(doclingDocumentParser.parse(any(), any())).thenAnswer(invocation -> {
            String filename = invocation.getArgument(0);
            String ext = DocumentParserRouter.fileExtension(filename);
            String metadata = """
                    {"schema_version":"v1","stable":{"source_file":"%s","file_ext":"%s","mime_type":"application/octet-stream"}}"""
                    .formatted(filename, ext);
            return new DocumentParseResult("# Mock parsed content\n\n正文内容", metadata);
        });

        parser = new TikaDocumentTextParser(
                cleaningService,
                doclingDocumentParser,
                new ObjectMapper(),
                properties(4000, false));
    }

    @Test
    @DisplayName("txt 文件应委托给 DoclingDocumentParser 解析")
    void parse_shouldDelegateTxtToDoclingParser() {
        String raw = """
                # 简历

                姓名：张三
                技术栈：Java
                """;

        DocumentParseResult result = parser.parse("resume.txt", raw.getBytes(StandardCharsets.UTF_8));

        verify(doclingDocumentParser).parse(eq("resume.txt"), any());
        assertTrue(result.cleanedMarkdown().contains("Mock parsed content"));
    }

    @Test
    @DisplayName("Markdown 文件应委托给 DoclingDocumentParser 解析")
    void parse_shouldDelegateMarkdownToDoclingParser() throws Exception {
        String raw = """
                # 接手文档质量回归清单

                ## 指标对照

                | 检查项 | 通过标准 |
                | --- | --- |
                | 标题层级稳定 | h1/h2 与正文边界清晰 |
                """;

        DocumentParseResult result = parser.parse("handoff.md", raw.getBytes(StandardCharsets.UTF_8));

        verify(doclingDocumentParser).parse(eq("handoff.md"), any());
        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("md", metadata.path("stable").path("file_ext").asText());
    }

    @Test
    @DisplayName("PDF 文件应委托给 DoclingDocumentParser 解析")
    void parse_shouldDelegatePdfToDoclingParser() {
        byte[] fakePdf = "%PDF-1.4 fake content".getBytes(StandardCharsets.UTF_8);

        DocumentParseResult result = parser.parse("report.pdf", fakePdf);

        verify(doclingDocumentParser).parse(eq("report.pdf"), any());
        assertTrue(result.cleanedMarkdown().contains("Mock parsed content"));
    }

    @Test
    @DisplayName("应正确传递文件名和内容给 DoclingDocumentParser")
    void parse_shouldPassFilenameAndContentToDoclingParser() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        ArgumentCaptor<String> filenameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);

        parser.parse("test.docx", content);

        verify(doclingDocumentParser).parse(filenameCaptor.capture(), contentCaptor.capture());
        assertEquals("test.docx", filenameCaptor.getValue());
        assertEquals("test content", new String(contentCaptor.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("不支持的格式应抛出 UnsupportedDocumentFormatException")
    void parse_shouldThrowOnUnsupportedFormat() {
        byte[] content = "csv content".getBytes(StandardCharsets.UTF_8);

        UnsupportedDocumentFormatException ex = assertThrows(
                UnsupportedDocumentFormatException.class,
                () -> parser.parse("data.csv", content));

        assertTrue(ex.getMessage().contains("csv"));
    }

    @Test
    @DisplayName("空内容应抛出 IllegalStateException")
    void parse_shouldThrowException_whenContentEmpty() {
        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse("empty.txt", new byte[0]));

        assertEquals("empty source content", ex.getMessage());
    }

    private static IngestProperties properties(int maxTextLength, boolean parseEmbeddedResource) {
        IngestProperties properties = new IngestProperties();
        properties.getParser().setMaxTextLength(maxTextLength);
        properties.getParser().setParseEmbeddedResource(parseEmbeddedResource);
        return properties;
    }
}
