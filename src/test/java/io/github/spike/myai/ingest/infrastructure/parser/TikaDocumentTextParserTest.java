package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TikaDocumentTextParser 单元测试。
 */
class TikaDocumentTextParserTest {

    @Test
    @DisplayName("应解析出 cleanedMarkdown 与 processingMetadata")
    void parse_shouldReturnParseArtifacts() throws Exception {
        TextCleaningService cleaningService = new TextCleaningService();
        TikaDocumentTextParser parser = new TikaDocumentTextParser(
                cleaningService,
                new ObjectMapper(),
                properties(2000, false));
        String raw = """
                # 简历

                姓名：张三
                image1.jpeg
                技术栈：Java
                """;

        DocumentParseResult result = parser.parse("resume.txt", raw.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.rawXhtml().contains("姓名"));
        assertTrue(result.cleanedMarkdown().contains("姓名"));
        assertTrue(result.cleanedMarkdown().contains("技术栈"));
        assertTrue(!result.cleanedMarkdown().contains("image1.jpeg"));

        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("resume.txt", metadata.path("stable").path("source_file").asText());
        assertEquals("txt", metadata.path("stable").path("file_ext").asText());
    }

    @Test
    @DisplayName("空内容应抛出异常")
    void parse_shouldThrowException_whenContentEmpty() {
        TextCleaningService cleaningService = new TextCleaningService();
        TikaDocumentTextParser parser = new TikaDocumentTextParser(
                cleaningService,
                new ObjectMapper(),
                properties(2000, false));

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> parser.parse("empty.txt", new byte[0]));

        assertEquals("empty source content", ex.getMessage());
    }

    @Test
    @DisplayName("超出最大文本长度时应抛出异常")
    void parse_shouldThrowException_whenExceedMaxTextLength() {
        TextCleaningService cleaningService = new TextCleaningService();
        TikaDocumentTextParser parser = new TikaDocumentTextParser(
                cleaningService,
                new ObjectMapper(),
                properties(20, false));
        String longText = "012345678901234567890123456789";

        assertThrows(
                IllegalStateException.class,
                () -> parser.parse("long.txt", longText.getBytes(StandardCharsets.UTF_8)));
    }

    private static IngestProperties properties(int maxTextLength, boolean parseEmbeddedResource) {
        IngestProperties properties = new IngestProperties();
        properties.getParser().setMaxTextLength(maxTextLength);
        properties.getParser().setParseEmbeddedResource(parseEmbeddedResource);
        return properties;
    }
}
