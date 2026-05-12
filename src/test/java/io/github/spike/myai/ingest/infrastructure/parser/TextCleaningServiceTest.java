package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TextCleaningService 单元测试。
 */
class TextCleaningServiceTest {

    private final TextCleaningService service = new TextCleaningService();

    @Test
    @DisplayName("应清理图片文件名、file URL 和分隔线噪音")
    void cleanText_shouldRemoveNoise() {
        String raw = """
                姓名：张三
                image1.jpeg
                file:///tmp/apache-tika-1234.html
                ----------
                技术栈：Java Spring
                """;

        String cleaned = service.cleanText(raw);

        assertFalse(cleaned.contains("image1.jpeg"));
        assertFalse(cleaned.contains("file:///tmp"));
        assertFalse(cleaned.contains("----------"));
        assertTrue(cleaned.contains("姓名：张三"));
        assertTrue(cleaned.contains("技术栈：Java Spring"));
    }

    @Test
    @DisplayName("应将 HTML 图片替换为占位文本并保留标题语义")
    void cleanHtmlAndMarkdown_shouldPreserveStructure() {
        String rawHtml = """
                <html><body>
                <p class="MsoTitle">第一章 背景</p>
                <p>正文内容</p>
                <img alt="流程图" src="demo.png" />
                </body></html>
                """;

        String cleanedHtml = service.cleanHtml(rawHtml);
        String markdown = service.toMarkdown(cleanedHtml);

        assertTrue(cleanedHtml.contains("<h1>第一章 背景</h1>"));
        assertTrue(cleanedHtml.contains("[图片: 流程图]"));
        assertTrue(markdown.contains("第一章 背景"));
        assertTrue(markdown.contains("流程图") || markdown.contains("图片"));
    }

    @Test
    @DisplayName("应规范换行和空格")
    void cleanText_shouldNormalizeWhitespace() {
        String raw = "A   B\r\n\r\n\r\nC";

        String cleaned = service.cleanText(raw);

        assertTrue(cleaned.contains("A B"));
        assertTrue(cleaned.contains("\n\nC"));
    }

    @Test
    @DisplayName("应保留 fenced code block 内部空格与缩进")
    void cleanText_shouldPreserveFencedCodeBlockWhitespace() {
        String raw = """
                标题

                ```java
                if (a  > b) {
                    return  1;
                }
                ```
                """;

        String cleaned = service.cleanText(raw);

        assertTrue(cleaned.contains("if (a  > b) {"));
        assertTrue(cleaned.contains("    return  1;"));
    }

    @Test
    @DisplayName("应保留缩进代码行内部空格")
    void cleanText_shouldPreserveIndentedCodeLineWhitespace() {
        String raw = """
                说明：

                    SELECT  *
                    FROM   demo_table
                """;

        String cleaned = service.cleanText(raw);

        assertTrue(cleaned.contains("    SELECT  *"));
        assertTrue(cleaned.contains("    FROM   demo_table"));
    }
}

