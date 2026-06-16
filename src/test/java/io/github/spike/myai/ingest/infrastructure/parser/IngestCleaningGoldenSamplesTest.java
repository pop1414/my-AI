package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.chunking.StructuredFallbackDocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ingest-cleaning 黄金样本资源回归测试。
 */
class IngestCleaningGoldenSamplesTest {

    private static final Path GOLDEN_ROOT = Path.of("src/test/resources/ingest-cleaning/golden");
    @ParameterizedTest(name = "{0}")
    @MethodSource("readmeCases")
    @DisplayName("黄金样本 README 应声明真实输入、验收锚点、噪音词与三面审阅重点")
    void readme_shouldDocumentRegressionAcceptanceAnchors(
            String sampleId, String inputFile, String expectedAnchor, String noiseTerm) throws Exception {
        Path sampleDir = GOLDEN_ROOT.resolve(sampleId);
        Path readme = sampleDir.resolve("README.md");

        String content = Files.readString(readme);

        assertTrue(Files.isRegularFile(sampleDir.resolve(inputFile)), inputFile + " should exist");
        assertTrue(content.contains(inputFile));
        assertTrue(content.contains("固定 QA 问题与预期命中锚点"));
        assertTrue(content.contains(expectedAnchor));
        assertTrue(content.contains("不应出现的噪音词"));
        assertTrue(content.contains(noiseTerm));
        assertTrue(content.contains("`cleaned.md`"));
        assertTrue(content.contains("`documents/chunks/preview`"));
        assertTrue(content.contains("`qa.ask`"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parseCases")
    @DisplayName("黄金样本真实输入应能被当前 Tika 解析链读出关键正文")
    void goldenInput_shouldBeParseableByCurrentParser(String sampleId, String inputFile, String expectedText)
            throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        Path input = GOLDEN_ROOT.resolve(sampleId).resolve(inputFile);

        DocumentParseResult result = parser.parse(inputFile, Files.readAllBytes(input));

        assertTrue(result.cleanedMarkdown().contains(expectedText), result.cleanedMarkdown());
    }

    @Test
    @DisplayName("Markdown 黄金样本应保留标题、代码块、表格、列表并排除外部噪音")
    void markdownGoldenInput_shouldPreserveNativeMarkdownStructure() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        Path input = GOLDEN_ROOT.resolve("md-001").resolve("project-handoff-checklist.md");

        DocumentParseResult result = parser.parse("project-handoff-checklist.md", Files.readAllBytes(input));
        String markdown = result.cleanedMarkdown();

        assertTrue(markdown.contains("# 接手文档质量回归清单"), markdown);
        assertTrue(markdown.contains("## 4. 指标对照"), markdown);
        assertTrue(markdown.contains("```bash\ncurl -X GET"), markdown);
        assertTrue(markdown.contains("| 检查项 | 通过标准 | 常见失真 |"), markdown);
        assertTrue(markdown.contains("  - 一级标题不能和正文粘连"), markdown);
        assertFalse(markdown.contains("内部评审稿"), markdown);
        assertFalse(markdown.contains("质检热线"), markdown);
        assertFalse(markdown.contains("控制台首页"), markdown);
        assertFalse(markdown.contains("页脚热线"), markdown);
    }

    @Test
    @DisplayName("Markdown 边界样本应区分代码块内容、raw HTML 噪音与 URL 噪音")
    void markdownEdgeCaseGoldenInput_shouldPreserveCodeExamplesAndCleanNoise() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        Path input = GOLDEN_ROOT.resolve("md-002").resolve("markdown-edge-cases.md");

        DocumentParseResult result = parser.parse("markdown-edge-cases.md", Files.readAllBytes(input));
        String markdown = result.cleanedMarkdown();

        assertTrue(markdown.contains("# Markdown 边界清洗样本"), markdown);
        assertTrue(markdown.contains("Setext 二级标题\n---"), markdown);
        assertTrue(markdown.contains("```html\n<script>\nalert(\"keep script example\");\n</script>"), markdown);
        assertTrue(markdown.contains("<iframe src=\"https://example.com/embed\"></iframe>"), markdown);
        assertTrue(markdown.contains("| 转义管道 | `a \\| b` | 保留单元格内容 |"), markdown);
        assertTrue(markdown.contains(">   - 再检查 chunks preview"), markdown);
        assertTrue(markdown.contains("    SELECT  *"), markdown);
        assertTrue(markdown.contains("正文中的图片链接 https://static.example.com/manual.png 应保留"), markdown);
        assertTrue(markdown.contains("正文中的本地路径提示 file:///docs/local-note.md 也应保留"), markdown);

        assertFalse(markdown.contains("remove script noise"), markdown);
        assertFalse(markdown.contains("https://noise.example.com"), markdown);
        assertFalse(markdown.contains("image42.png"), markdown);
        assertFalse(markdown.contains("file:///tmp/tika-cache.html"), markdown);
        assertFalse(markdown.contains("内部评审稿"), markdown);
        assertFalse(markdown.contains("质检热线"), markdown);
        assertFalse(markdown.contains("控制台首页"), markdown);
        assertFalse(markdown.contains("页脚热线"), markdown);
    }

    @Test
    @DisplayName("HTML 黄金样本应保留 main 正文并清理导航、侧栏和页脚噪音")
    void htmlGoldenInput_shouldKeepMainContentAndRemovePageChrome() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        Path input = GOLDEN_ROOT.resolve("html-001").resolve("support-workflow.html");

        DocumentParseResult result = parser.parse("support-workflow.html", Files.readAllBytes(input));
        String markdown = result.cleanedMarkdown();

        assertTrue(markdown.contains("支持工单分流说明"), markdown);
        assertTrue(markdown.contains("分流目标"), markdown);
        assertTrue(markdown.contains("人工复核触发条件"), markdown);
        assertTrue(markdown.contains("同一段正文被错误切成三段以上"), markdown);
        assertTrue(markdown.contains("列表或表格被拍平成普通文本"), markdown);
        assertTrue(markdown.contains("正文抽取和噪音清理边界出了问题"), markdown);

        assertFalse(markdown.contains("控制台首页"), markdown);
        assertFalse(markdown.contains("队列总览"), markdown);
        assertFalse(markdown.contains("运行指标"), markdown);
        assertFalse(markdown.contains("相关阅读"), markdown);
        assertFalse(markdown.contains("页脚热线：400-100-2000"), markdown);
        assertFalse(markdown.contains("更新时间：2026-05-13"), markdown);
    }

    @Test
    @DisplayName("HTML 黄金样本分块预览应保留正文标题 sourceHint")
    void htmlGoldenInput_shouldKeepChunkSourceHintForMainHeadings() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        StructuredFallbackDocumentChunker chunker = new StructuredFallbackDocumentChunker(properties());
        Path input = GOLDEN_ROOT.resolve("html-001").resolve("support-workflow.html");

        DocumentParseResult result = parser.parse("support-workflow.html", Files.readAllBytes(input));
        List<DocumentChunk> chunks = chunker.chunk(result.cleanedMarkdown());

        assertTrue(chunks.stream().anyMatch(chunk ->
                "{\"heading\":\"分流目标\"}".equals(chunk.sourceHint().toStorageValue())
                        && chunk.content().contains("区分普通文本清洗问题")), result.cleanedMarkdown());
        assertTrue(chunks.stream().anyMatch(chunk ->
                "{\"heading\":\"人工复核触发条件\"}".equals(chunk.sourceHint().toStorageValue())
                        && chunk.content().contains("同一段正文被错误切成三段以上")), result.cleanedMarkdown());
        assertFalse(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint().toStorageValue() != null
                        && chunk.sourceHint().toStorageValue().contains("面向文档回归值班同学的内部说明页")),
                result.cleanedMarkdown());
    }

    @Test
    @DisplayName("Word 黄金样本应保留标题、列表、表格和图片说明并排除 OpenXML 元数据噪音")
    void wordGoldenInput_shouldPreserveOfficeStructureAndRemovePackageNoise() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        Path input = GOLDEN_ROOT.resolve("word-001").resolve("knowledge-base-review-checklist.docx");

        DocumentParseResult result = parser.parse("knowledge-base-review-checklist.docx", Files.readAllBytes(input));
        String markdown = result.cleanedMarkdown();

        assertTrue(markdown.contains("# 知识库文档上线前核对清单"), markdown);
        assertTrue(markdown.contains("# 上线前核对项"), markdown);
        assertContainsInOrder(
                markdown,
                "- 确认知识库名称与文档主题一致",
                "- 确认文档中的敏感信息已经脱敏",
                "- 确认固定 QA 问题能够从正文直接定位答案");
        assertTrue(markdown.contains("# 图片说明保留建议"), markdown);
        assertTrue(markdown.contains("图示说明：当原文中存在流程图或示意图时"), markdown);
        assertTrue(markdown.contains("# 回归风险对照"), markdown);
        assertTrue(markdown.contains("| 风险项 | 表现 | 回归关注点 |"), markdown);
        assertTrue(markdown.contains("| 表格被拍平 | 表格列顺序丢失，内容连成一段 |"), markdown);
        assertTrue(markdown.contains("| 图片说明缺失 | 图片占位或说明文字被删除 |"), markdown);
        assertTrue(markdown.contains("documents/chunks/preview 是否仍能给出结构化上下文"), markdown);

        assertFalse(markdown.contains("Codex sample generator"), markdown);
        assertFalse(markdown.contains("ingest-cleaning, golden, docx"), markdown);
        assertFalse(markdown.contains("docProps"), markdown);
        assertFalse(markdown.contains("word/_rels"), markdown);
    }

    @Test
    @DisplayName("弱结构 PDF 黄金样本应修复段落、保持标题边界并清理页眉页脚页码噪音")
    void weakPdfGoldenInput_shouldRepairParagraphsKeepHeadingBoundariesAndRemoveHeaderFooterNoise() throws Exception {
        TikaDocumentTextParser parser =
                new TikaDocumentTextParser(new TextCleaningService(), new ObjectMapper(), properties());
        StructuredFallbackDocumentChunker chunker = new StructuredFallbackDocumentChunker(properties());
        Path input = GOLDEN_ROOT.resolve("weak-pdf-001").resolve("weak-pdf-regression-sample.pdf");

        DocumentParseResult result = parser.parse("weak-pdf-regression-sample.pdf", Files.readAllBytes(input));
        String markdown = result.cleanedMarkdown();
        List<DocumentChunk> chunks = chunker.chunk(markdown);

        assertTrue(markdown.contains("第一节 可分块文本基底"), markdown);
        assertTrue(markdown.contains("在进入 chunking 之前，已经尽量消除了噪音"), markdown);
        assertTrue(markdown.contains(
                "可分块文本基底指的是在进入 chunking 之前，已经尽量消除了噪音、错误断段和标题漂移问题的正文文本。"),
                markdown);
        assertTrue(markdown.contains(
                "如果这个阶段的文本本身已经不稳定，后续不管采用普通分块还是父子分块，都只会把错误边界继续向后传递。"),
                markdown);
        assertTrue(markdown.contains("第二节 人工复核建议"), markdown);
        assertTrue(markdown.contains(
                "第二节 人工复核建议\n当同一自然段被切成三段以上，或者标题和正文粘连在一起时，不要先去调向量参数"),
                markdown);
        assertTrue(markdown.contains("第三节 回归观察点"), markdown);
        assertTrue(chunks.stream().anyMatch(chunk ->
                "{\"heading\":\"第一节 可分块文本基底\"}".equals(chunk.sourceHint().toStorageValue())
                        && chunk.content().contains("错误断段和标题漂移问题")), markdown);
        assertTrue(chunks.stream().anyMatch(chunk ->
                "{\"heading\":\"第二节 人工复核建议\"}".equals(chunk.sourceHint().toStorageValue())
                        && chunk.content().contains("不要先去调向量参数")), markdown);

        assertFalse(markdown.contains("内部评审稿"), markdown);
        assertFalse(markdown.contains("第 1 页"), markdown);
        assertFalse(markdown.contains("第 2 页"), markdown);
        assertFalse(markdown.contains("质检热线 400-900-1200"), markdown);
        assertFalse(markdown.contains("噪音、\n错误断段"), markdown);
        assertFalse(markdown.contains("人工复核建议 当同一自然段"), markdown);
        assertFalse(chunks.stream().anyMatch(chunk ->
                chunk.sourceHint().toStorageValue() != null
                        && chunk.sourceHint().toStorageValue().contains("当同一自然段")), markdown);
    }

    private static Stream<Arguments> readmeCases() {
        return Stream.of(
                Arguments.of("weak-pdf-001", "weak-pdf-regression-sample.pdf", "第一节 可分块文本基底", "内部评审稿"),
                Arguments.of(
                        "word-001",
                        "knowledge-base-review-checklist.docx",
                        "上线前核对项",
                        "Codex sample generator"),
                Arguments.of("md-001", "project-handoff-checklist.md", "指标对照", "质检热线"),
                Arguments.of("md-002", "markdown-edge-cases.md", "代码块中的 HTML 示例", "remove script noise"),
                Arguments.of("html-001", "support-workflow.html", "人工复核触发条件", "控制台首页"));
    }

    private static Stream<Arguments> parseCases() {
        return Stream.of(
                Arguments.of("weak-pdf-001", "weak-pdf-regression-sample.pdf", "chunking"),
                Arguments.of("word-001", "knowledge-base-review-checklist.docx", "上线前核对项"),
                Arguments.of("md-001", "project-handoff-checklist.md", "结构化检查表"),
                Arguments.of("md-002", "markdown-edge-cases.md", "keep script example"),
                Arguments.of("html-001", "support-workflow.html", "人工复核触发条件"));
    }

    private static IngestProperties properties() {
        IngestProperties properties = new IngestProperties();
        properties.getParser().setMaxTextLength(20_000);
        properties.getParser().setParseEmbeddedResource(false);
        return properties;
    }

    private static void assertContainsInOrder(String content, String... expectedParts) {
        int previousIndex = -1;
        for (String expectedPart : expectedParts) {
            int currentIndex = content.indexOf(expectedPart);
            assertTrue(currentIndex > previousIndex, content);
            previousIndex = currentIndex;
        }
    }
}
