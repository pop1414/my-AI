package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static Stream<Arguments> readmeCases() {
        return Stream.of(
                Arguments.of("weak-pdf-001", "weak-pdf-regression-sample.pdf", "第一节 可分块文本基底", "内部评审稿"),
                Arguments.of(
                        "word-001",
                        "knowledge-base-review-checklist.docx",
                        "上线前核对项",
                        "Codex sample generator"),
                Arguments.of("md-001", "project-handoff-checklist.md", "指标对照", "质检热线"),
                Arguments.of("html-001", "support-workflow.html", "人工复核触发条件", "控制台首页"));
    }

    private static Stream<Arguments> parseCases() {
        return Stream.of(
                Arguments.of("weak-pdf-001", "weak-pdf-regression-sample.pdf", "chunking"),
                Arguments.of("word-001", "knowledge-base-review-checklist.docx", "上线前核对项"),
                Arguments.of("md-001", "project-handoff-checklist.md", "结构化检查表"),
                Arguments.of("html-001", "support-workflow.html", "人工复核触发条件"));
    }

    private static IngestProperties properties() {
        IngestProperties properties = new IngestProperties();
        properties.getParser().setMaxTextLength(20_000);
        properties.getParser().setParseEmbeddedResource(false);
        return properties;
    }
}
