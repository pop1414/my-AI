package io.github.spike.myai.ingest.infrastructure.parser;

import org.springframework.stereotype.Component;

/**
 * 文本清洗 facade。
 *
 * <p>Docling 迁移后仅保留原生 Markdown 最小破坏清洗能力。
 * 具体清洗规则收敛到 {@link MarkdownTextCleaner}，避免调用方感知清洗规则的组织细节。
 *
 * <p>{@link #cleanHtml} 和 {@link #toMarkdown} 已标记废弃，
 * 仅保留供 {@link TikaDocumentTextParser} 临时调用，Story 3.2 删除 Tika 后一并移除。
 *
 * <p>退出条件：黄金样本清洗前后 diff 为零差异时可移除（记录为 no-op）。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class TextCleaningService {

    private final MarkdownTextCleaner markdownTextCleaner = new MarkdownTextCleaner();

    /**
     * 清洗原始 HTML/XHTML（已废弃，仅供 TikaDocumentTextParser 临时调用）。
     *
     * @param rawHtml 原始 HTML/XHTML 内容
     * @return 原样返回（HTML 清洗能力已移除）
     * @deprecated Story 3.2 删除 TikaDocumentTextParser 后一并移除
     */
    @Deprecated(since = "2.5", forRemoval = true)
    public String cleanHtml(String rawHtml) {
        return rawHtml;
    }

    /**
     * 将 HTML 转换为 Markdown（已废弃，仅供 TikaDocumentTextParser 临时调用）。
     *
     * @param cleanedHtml 语义清洗后的 HTML
     * @return 原样返回（HTML→Markdown 转换能力已移除）
     * @deprecated Story 3.2 删除 TikaDocumentTextParser 后一并移除
     */
    @Deprecated(since = "2.5", forRemoval = true)
    public String toMarkdown(String cleanedHtml) {
        return cleanedHtml;
    }

    /**
     * 对原生 Markdown 执行最小破坏清洗。
     *
     * <p>清洗内容：统一换行符（CRLF→LF）、去除控制字符、压缩连续空行。
     *
     * @param rawMarkdown 原生 Markdown 文本
     * @return 最小规整后的 Markdown
     */
    public String cleanNativeMarkdown(String rawMarkdown) {
        return markdownTextCleaner.cleanNativeMarkdown(rawMarkdown);
    }

    static String repairMarkdownStructure(String markdown) {
        return MarkdownStructureRepairer.repair(markdown);
    }
}
