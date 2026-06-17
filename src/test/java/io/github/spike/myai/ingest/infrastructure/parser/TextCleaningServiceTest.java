package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TextCleaningService} 单元测试。
 *
 * <p>覆盖两条核心清洗路径：
 * <ul>
 *   <li>{@link TextCleaningService#cleanNativeMarkdown(String)}
 *       —— 原生 Markdown 最小破坏清洗；</li>
 *   <li>{@code TextCleaningService.repairMarkdownStructure(String)}
 *       —— Markdown 结构修复（Word 圆点列表、软换行、粘连标题）。</li>
 * </ul>
 *
 * <p>测试重点：噪音去除（图片文件名/URL）、结构保留（标题/代码块/表格/列表缩进）、
 * 危险 HTML 标签清除（代码块外）与保留（代码块内）。
 *
 * @author Spike
 * @since 1.0.0
 */
class TextCleaningServiceTest {

    /** 被测试的文本清洗服务实例（无状态，可直接复用） */
    private final TextCleaningService service = new TextCleaningService();

    /**
     * 验证 Markdown 结构修复应保留 Word 圆点列表的基本缩进层级。
     */
    @Test
    @DisplayName("Markdown 结构修复应保留 Word 圆点列表的基本缩进层级")
    void repairMarkdownStructure_shouldPreserveWordBulletIndentation() {
        String markdown = """
                · 一级核对项

                  · 二级核对项
                """;

        String repaired = TextCleaningService.repairMarkdownStructure(markdown);

        assertTrue(repaired.contains("- 一级核对项"), repaired);
        assertTrue(repaired.contains("  - 二级核对项"), repaired);
    }

    /**
     * 验证 Markdown 结构修复应修复弱结构 PDF 软换行并拆开粘连标题。
     */
    @Test
    @DisplayName("Markdown 结构修复应修复弱结构 PDF 软换行并拆开粘连标题")
    void repairMarkdownStructure_shouldRepairWeakPdfSoftWrapsAndGluedHeading() {
        String markdown = """
                文档清洗回归说明

                第一节 可分块文本基底
                可分块文本基底指的是在进入 chunking 之前，已经尽量消除了噪音、
                错误断段和标题漂移问题的正文文本。如果这个阶段的文本本身已经不稳定，
                后续不管采用普通分块还是父子分块，都只会把错误边界继续向后传递。

                第二节 人工复核建议 当同一自然段被切成三段以上，或者标题和正文粘连在一起时，
                不要先去调向量参数，而应先回到 parser 和 cleaner 检查输入质量。

                第三节 回归观察点
                1. cleaned.md 是否恢复了自然段连续性。
                2. documents/chunks/preview 是否仍沿着正确标题边界切分。
                """;

        String repaired = TextCleaningService.repairMarkdownStructure(markdown);

        assertTrue(repaired.contains(
                "可分块文本基底指的是在进入 chunking 之前，已经尽量消除了噪音、错误断段和标题漂移问题的正文文本。"),
                repaired);
        assertTrue(repaired.contains(
                "如果这个阶段的文本本身已经不稳定，后续不管采用普通分块还是父子分块，都只会把错误边界继续向后传递。"),
                repaired);
        assertTrue(repaired.contains("第二节 人工复核建议\n当同一自然段被切成三段以上，或者标题和正文粘连在一起时，不要先去调向量参数"),
                repaired);
        assertTrue(repaired.contains("继续向后传递。\n\n第二节 人工复核建议"), repaired);
        assertTrue(repaired.contains("第三节 回归观察点\n1. cleaned.md 是否恢复了自然段连续性。"), repaired);
        assertFalse(repaired.contains("人工复核建议 当同一自然段"), repaired);
    }

    /**
     * 验证 Markdown 结构修复不应拼接围栏代码块内部行。
     */
    @Test
    @DisplayName("Markdown 结构修复不应拼接围栏代码块内部行")
    void repairMarkdownStructure_shouldNotJoinLinesInsideFencedCodeBlocks() {
        String markdown = """
                说明段第一行
                说明段第二行

                ```text
                code line one
                code line two
                ```
                """;

        String repaired = TextCleaningService.repairMarkdownStructure(markdown);

        assertTrue(repaired.contains("说明段第一行说明段第二行"), repaired);
        assertTrue(repaired.contains("```text\ncode line one\ncode line two\n```"), repaired);
        assertFalse(repaired.contains("code line one code line two"), repaired);
    }

    /**
     * 验证原生 Markdown 清洗路径（{@code .md} 文件直通）：
     * <ul>
     *   <li>BOM（U+FEFF）应被移除；</li>
     *   <li>Markdown 标题、列表缩进、GFM 表格、围栏代码块均保留原结构；</li>
     *   <li>图片文件名、file:// URL、&lt;script&gt; 标签应被清除。</li>
     * </ul>
     */
    @Test
    @DisplayName("原生 Markdown 清洗应保留表格、列表缩进和代码块围栏")
    void cleanNativeMarkdown_shouldPreserveMarkdownStructure() {
        String raw = """
                ﻿# 标题

                - 一级
                  - 二级

                | 检查项 | 通过标准 |
                | --- | --- |
                | 标题 | 保留层级 |

                ```bash
                curl  -X GET "http://localhost"
                ```

                image1.png
                file:///tmp/tika-cache.html
                <script>alert("x")</script>
                """;

        String cleaned = service.cleanNativeMarkdown(raw);

        assertTrue(cleaned.contains("# 标题"));
        assertTrue(cleaned.contains("  - 二级"));
        assertTrue(cleaned.contains("| 检查项 | 通过标准 |"));
        assertTrue(cleaned.contains("```bash"));
        assertTrue(cleaned.contains("curl  -X GET"));
        assertFalse(cleaned.contains("image1.png"));
        assertFalse(cleaned.contains("file:///tmp"));
        assertFalse(cleaned.contains("<script>"));
    }

    /**
     * 验证代码块内外的危险 HTML 标签差异化处理：
     * <ul>
     *   <li>代码块外的 {@code <script>alert("remove")</script>} 应被清除；</li>
     *   <li>围栏代码块内的 {@code <script>...</script>} 应原样保留，
     *       因为它是代码示例而非真实攻击载体。</li>
     * </ul>
     */
    @Test
    @DisplayName("原生 Markdown 清洗不应删除代码块内的危险 HTML 示例")
    void cleanNativeMarkdown_shouldKeepDangerousHtmlExamplesInsideFencedCodeBlock() {
        String raw = """
                # HTML 示例

                <script>alert("remove")</script>

                ```html
                <script>
                alert("keep");
                </script>
                ```
                """;

        String cleaned = service.cleanNativeMarkdown(raw);

        assertFalse(cleaned.contains("alert(\"remove\")"));
        assertTrue(cleaned.contains("```html"));
        assertTrue(cleaned.contains("<script>\nalert(\"keep\");\n</script>"));
    }
}
