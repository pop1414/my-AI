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

    @Test
    @DisplayName("HTML 清洗后的独立短标题应生成可解释 sourceHint")
    void chunk_shouldKeepSourceHintForPlainHtmlHeadings() {
        StructuredFallbackDocumentChunker chunker = new StructuredFallbackDocumentChunker(properties());
        String cleanedMarkdown = """
                支持工单分流说明

                面向文档回归值班同学的内部说明页

                分流目标

                这份页面用于说明值班同学如何区分普通文本清洗问题、结构退化问题和需要人工升级的问题。

                人工复核触发条件

                * 同一段正文被错误切成三段以上，影响后续问答连续性。
                * 列表或表格被拍平成普通文本，导致关键信息位置无法稳定引用。
                """;

        List<DocumentChunk> chunks = chunker.chunk(cleanedMarkdown);

        assertTrue(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint() != null
                        && chunk.sourceHint().contains("分流目标")
                        && chunk.content().contains("区分普通文本清洗问题")));
        assertTrue(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint() != null
                        && chunk.sourceHint().contains("人工复核触发条件")
                        && chunk.content().contains("同一段正文被错误切成三段以上")));
    }

    private static IngestProperties properties() {
        IngestProperties properties = new IngestProperties();
        properties.getChunk().setChunkSize(80);
        properties.getChunk().setOverlapSize(0);
        return properties;
    }
}
