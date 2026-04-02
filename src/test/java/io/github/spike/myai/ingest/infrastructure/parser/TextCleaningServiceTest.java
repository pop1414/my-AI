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
    @DisplayName("应规范换行和空格")
    void cleanText_shouldNormalizeWhitespace() {
        String raw = "A   B\r\n\r\n\r\nC";

        String cleaned = service.cleanText(raw);

        assertTrue(cleaned.contains("A B"));
        assertTrue(cleaned.contains("\n\nC"));
    }
}

