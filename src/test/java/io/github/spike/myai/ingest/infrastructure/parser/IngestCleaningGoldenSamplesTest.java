package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ingest-cleaning 黄金样本资源回归测试。
 *
 * <p>旧基线的解析测试已在 Story 3.2 删除（旧解析器已移除）。
 * Docling 基线的解析测试将在 Story 4.3 重建。
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
}
