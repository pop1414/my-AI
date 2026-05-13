package io.github.spike.myai.ingest.infrastructure.chunking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * StructuredFallbackDocumentChunker 单元测试。
 */
class StructuredFallbackDocumentChunkerTest {

    @Test
    @DisplayName("Markdown 样本分块预览应保留可解释的标题 sourceHint 和结构边界")
    void chunk_shouldKeepMarkdownSourceHintAndStructureBoundaries() {
        StructuredFallbackDocumentChunker chunker = new StructuredFallbackDocumentChunker(properties());
        String markdown = """
                # 接手文档质量回归清单

                ## 4. 指标对照

                | 检查项 | 通过标准 | 常见失真 |
                | --- | --- | --- |
                | 标题层级稳定 | `h1/h2` 与正文边界清晰可见 | 标题丢失、标题和正文粘连 |

                ## 5. 示例命令

                ```bash
                curl -X GET "http://localhost:8080/api/v1/documents/{documentId}/chunks/preview?limit=5"
                ```
                """;

        List<DocumentChunk> chunks = chunker.chunk(markdown);

        assertTrue(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint() != null
                        && chunk.sourceHint().contains("4. 指标对照")
                        && chunk.content().contains("标题层级稳定")));
        assertTrue(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint() != null
                        && chunk.sourceHint().contains("5. 示例命令")
                        && chunk.content().contains("curl")));
    }

    private static IngestProperties properties() {
        IngestProperties properties = new IngestProperties();
        properties.getChunk().setChunkSize(80);
        properties.getChunk().setOverlapSize(0);
        return properties;
    }
}
