package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessingMetadataBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessingMetadataBuilder builder = new ProcessingMetadataBuilder(objectMapper);

    @Test
    @DisplayName("metadata 应从 Tika 字段和 Markdown 标题构建稳定结构")
    void build_shouldIncludeStableAndConditionalMetadata() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        metadata.set("language", "zh-CN");
        metadata.set("xmpTPg:NPages", "12");
        metadata.set(TikaCoreProperties.TITLE, "Tika 标题");

        JsonNode root = objectMapper.readTree(builder.build(
                "report.PDF",
                metadata,
                """
                # Markdown 标题

                ## 第二标题
                """));

        assertEquals("v1", root.path("schema_version").asText());
        assertEquals("report.PDF", root.path("stable").path("source_file").asText());
        assertEquals("pdf", root.path("stable").path("file_ext").asText());
        assertEquals("application/pdf", root.path("stable").path("mime_type").asText());
        assertEquals("high", root.path("stable").path("quality").asText());
        assertTrue(root.path("stable").path("created_at").asText().contains("T"));
        assertEquals("zh-CN", root.path("conditional").path("language").asText());
        assertEquals(12, root.path("conditional").path("page_count").asInt());
        assertEquals("Tika 标题", root.path("conditional").path("primary_title").asText());
        assertEquals("Markdown 标题", root.path("conditional").path("title_outline_sample").get(0).asText());
        assertTrue(root.path("best_effort").isObject());
    }

    @Test
    @DisplayName("缺少 Tika title 时应使用 Markdown 首个标题作为 primary_title")
    void build_shouldFallbackPrimaryTitleToMarkdownHeading() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown");

        JsonNode root = objectMapper.readTree(builder.build("note.md", metadata, "# 文档标题\n\n正文"));

        assertEquals("文档标题", root.path("conditional").path("primary_title").asText());
        assertEquals("文档标题", root.path("conditional").path("title_outline_sample").get(0).asText());
    }
}
