package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TextCleaningService} 单元测试。
 *
 * <p>覆盖三条核心清洗路径：
 * <ul>
 *   <li>{@link TextCleaningService#cleanText(String)} —— 通用 Markdown/纯文本规整；</li>
 *   <li>{@link TextCleaningService#cleanHtml(String)} + {@link TextCleaningService#toMarkdown(String)}
 *       —— XHTML → HTML 语义清洗 → Markdown 转换；</li>
 *   <li>{@link TextCleaningService#cleanNativeMarkdown(String)}
 *       —— 原生 Markdown 最小破坏清洗。</li>
 * </ul>
 *
 * <p>测试重点：噪音去除（图片/URL/分隔线）、结构保留（标题/代码块/表格/列表缩进）、
 * 白空间规范化和危险 HTML 标签清除。
 *
 * @author Spike
 * @since 1.0.0
 */
class TextCleaningServiceTest {

    /** 被测试的文本清洗服务实例（无状态，可直接复用） */
    private final TextCleaningService service = new TextCleaningService();

    /**
     * 验证 {@link TextCleaningService#cleanText} 能正确移除图片文件名、
     * 本地 file:// URL 和无意义分隔线，同时保留正文内容。
     */
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

    /**
     * 验证 HTML 清洗链路：
     * <ol>
     *   <li>MsoTitle 样式标签 → 标准 &lt;h1&gt; 标签；</li>
     *   <li>&lt;img&gt; 标签 → [图片: alt文本] 占位符；</li>
     *   <li>cleaned.html → Markdown 转换后标题和图片语义均保留。</li>
     * </ol>
     */
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
    @DisplayName("HTML 转 Markdown 应保留 ATX 标题、Word 圆点列表和表格表头顺序")
    void toMarkdown_shouldRepairWordLikeMarkdownStructure() {
        String cleanedHtml = """
                <h1>知识库文档上线前核对清单</h1>
                <h1>上线前核对项</h1>
                <p>· 确认知识库名称与文档主题一致</p>
                <p>· 确认文档中的敏感信息已经脱敏</p>
                <table>
                  <tbody>
                    <tr><td>风险项</td><td>表现</td><td>回归关注点</td></tr>
                    <tr><td>表格被拍平</td><td>表格列顺序丢失</td><td>chunks preview 是否可解释</td></tr>
                  </tbody>
                </table>
                """;

        String markdown = service.toMarkdown(cleanedHtml);

        assertTrue(markdown.contains("# 知识库文档上线前核对清单"), markdown);
        assertTrue(markdown.contains("# 上线前核对项"), markdown);
        assertTrue(markdown.contains("- 确认知识库名称与文档主题一致"), markdown);
        assertTrue(markdown.contains("- 确认文档中的敏感信息已经脱敏"), markdown);
        assertTrue(markdown.contains("| 风险项 | 表现 | 回归关注点 |"), markdown);
        assertTrue(markdown.indexOf("| 风险项 | 表现 | 回归关注点 |")
                < markdown.indexOf("|-------|"), markdown);
        assertTrue(markdown.contains("| 表格被拍平 | 表格列顺序丢失 | chunks preview 是否可解释 |"), markdown);
    }

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
        assertTrue(repaired.contains(
                "第二节 人工复核建议\n当同一自然段被切成三段以上，或者标题和正文粘连在一起时，不要先去调向量参数"),
                repaired);
        assertTrue(repaired.contains("继续向后传递。\n\n第二节 人工复核建议"), repaired);
        assertTrue(repaired.contains("第三节 回归观察点\n1. cleaned.md 是否恢复了自然段连续性。"), repaired);
        assertFalse(repaired.contains("人工复核建议 当同一自然段"), repaired);
    }

    @Test
    @DisplayName("HTML 转 Markdown 结构修复不应改写代码块内的圆点和表格样例")
    void toMarkdown_shouldNotRepairWordMarkersInsideCodeBlocks() {
        String cleanedHtml = """
                <pre><code>· literal bullet
                |---|---|
                | code | row |
                </code></pre>
                """;

        String markdown = service.toMarkdown(cleanedHtml);

        assertTrue(markdown.contains("· literal bullet"), markdown);
        assertFalse(markdown.contains("- literal bullet"), markdown);
        assertTrue(markdown.indexOf("|---|---|") < markdown.indexOf("| code | row |"), markdown);
    }

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
     * 验证多空格合并和连续空行压缩：
     * "A   B" → "A B"；三个空行 → 两个空行。
     */
    @Test
    @DisplayName("应规范换行和空格")
    void cleanText_shouldNormalizeWhitespace() {
        String raw = "A   B\r\n\r\n\r\nC";

        String cleaned = service.cleanText(raw);

        assertTrue(cleaned.contains("A B"));
        assertTrue(cleaned.contains("\n\nC"));
    }

    /**
     * 验证围栏代码块（{@code ```}...{@code ```}）内部的多余空格和缩进
     * 不会被压缩，保持代码原样。
     */
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

    /**
     * 验证缩进代码行（以 4 空格或 1 制表符开头）内部的多余空格
     * 不会被压缩，保留 SQL 等代码格式。
     */
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
                \uFEFF# 标题

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
