package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TextCleaningService 图片资源清洗回归测试。
 *
 * <p>验证 reader.md 允许保留图片资源引用时，cleaned.md 仍会降噪为可分块正文，
 * 避免 markdown 图片链接或 data URI 进入 RAG 主链。
 *
 * @author spike
 * @since 1.0.0
 */
class TextCleaningServiceImageReferenceTest {

    /** 原生 Markdown 清洗服务，无状态，可直接复用。 */
    private final TextCleaningService service = new TextCleaningService();

    /** 验证图片资源引用应被移除，仅保留最小可读描述。 */
    @Test
    @DisplayName("原生 Markdown 清洗应移除图片资源引用并保留最小描述")
    void cleanNativeMarkdown_shouldReplaceImagesWithMinimalDescription() {
        String raw = """
                # 图片样本

                ![系统流程图](https://cdn.example.com/diagram.png)
                ![](data:image/png;base64,AAAA)
                <img alt="部署拓扑" src="file:///tmp/topology.png" />
                ![image](https://cdn.example.com/noise.png)
                正文说明
                """;

        String cleaned = service.cleanNativeMarkdown(raw);

        assertTrue(cleaned.contains("系统流程图"), cleaned);
        assertTrue(cleaned.contains("部署拓扑"), cleaned);
        assertTrue(cleaned.contains("正文说明"), cleaned);
        assertFalse(cleaned.contains("https://cdn.example.com/diagram.png"), cleaned);
        assertFalse(cleaned.contains("data:image/png;base64"), cleaned);
        assertFalse(cleaned.contains("file:///tmp/topology.png"), cleaned);
        assertFalse(cleaned.contains("!["), cleaned);
        assertFalse(cleaned.contains("<img"), cleaned);
        assertFalse(cleaned.contains("\nimage\n"), cleaned);
    }
}
