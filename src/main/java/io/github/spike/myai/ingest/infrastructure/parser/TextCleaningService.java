package io.github.spike.myai.ingest.infrastructure.parser;

import org.springframework.stereotype.Component;

/**
 * 文本清洗 facade。
 *
 * <p>对外保留 parser 当前需要学习的最小 Interface；具体 HTML 清洗、Markdown 行级清洗和
 * 结构修复规则收敛到内部 Module，避免调用方感知清洗规则的组织细节。
 */
@Component
public class TextCleaningService {

    private final HtmlSemanticCleaner htmlSemanticCleaner = new HtmlSemanticCleaner();
    private final HtmlToMarkdownRenderer htmlToMarkdownRenderer = new HtmlToMarkdownRenderer();
    private final MarkdownTextCleaner markdownTextCleaner = new MarkdownTextCleaner();

    /**
     * 清洗原始 HTML/XHTML，输出语义更稳定的 HTML。
     *
     * @param rawHtml 原始 HTML/XHTML 内容
     * @return 语义清洗后的 HTML
     */
    public String cleanHtml(String rawHtml) {
        return htmlSemanticCleaner.clean(rawHtml);
    }

    /**
     * 将语义清洗后的 HTML 转换为 Markdown。
     *
     * @param cleanedHtml 语义清洗后的 HTML
     * @return 转换并规整后的 Markdown 内容
     */
    public String toMarkdown(String cleanedHtml) {
        if (cleanedHtml == null || cleanedHtml.isBlank()) {
            return "";
        }
        String markdown = htmlToMarkdownRenderer.render(cleanedHtml);
        return markdownTextCleaner.cleanConvertedMarkdown(markdown);
    }

    /**
     * 对原生 Markdown 执行最小破坏清洗。
     *
     * @param rawMarkdown 原生 Markdown 文本
     * @return 最小规整后的 Markdown
     */
    public String cleanNativeMarkdown(String rawMarkdown) {
        return markdownTextCleaner.cleanNativeMarkdown(rawMarkdown);
    }

    /**
     * 对 Markdown/纯文本执行轻量规整。
     *
     * @param rawText 原始文本
     * @return 规整后的文本
     */
    public String cleanText(String rawText) {
        return markdownTextCleaner.cleanText(rawText);
    }

    static String repairMarkdownStructure(String markdown) {
        return MarkdownStructureRepairer.repair(markdown);
    }
}
