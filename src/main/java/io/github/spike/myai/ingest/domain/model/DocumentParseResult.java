package io.github.spike.myai.ingest.domain.model;

/**
 * 文档解析结果（Domain Value Object / Result）。
 *
 * <p>承载一期处理中间产物链路的核心结果：
 * <ul>
 *   <li>{@code rawXhtml}：Tika 直接产出的原始 XHTML</li>
 *   <li>{@code cleanedHtml}：经 Jsoup 语义清洗后的 HTML</li>
 *   <li>{@code cleanedMarkdown}：作为主链产物的 cleaned.md 内容，不可为空</li>
 *   <li>{@code processingMetadata}：文档级处理结果元数据 JSON 字符串，
 *       由 parser 在解析阶段生成，并在终态时回填到数据库</li>
 * </ul>
 *
 * <p>设计意图：将原本只返回纯文本字符串的解析端口升级为结构化结果，
 * 使得下游（分块、向量化、状态收口）可以同时消费 cleanedMarkdown 和 processingMetadata。
 *
 * @author Spike
 * @since 1.0.0
 */
public record DocumentParseResult(
        /** Tika 直接产出的原始 XHTML，用于调试和审计 */
        String rawXhtml,
        /** 经 Jsoup 语义清洗后的 HTML，去除样式/脚本/噪音标签 */
        String cleanedHtml,
        /** 清洗后经 flexmark 转换的 Markdown 主链产物，作为分块输入 */
        String cleanedMarkdown,
        /** 文档级处理结果元数据 JSON 字符串，在终态回填到 ingest_documents.processing_metadata */
        String processingMetadata) {

    /**
     * 紧凑构造器：校验主链产物不可为空。
     *
     * <p>cleanedMarkdown 是后续分块和向量化的唯一输入来源，
     * 若为空则整个处理链路无法继续，因此必须在构造阶段拦截。
     */
    public DocumentParseResult {
        if (cleanedMarkdown == null || cleanedMarkdown.isBlank()) {
            throw new IllegalArgumentException("cleanedMarkdown must not be blank");
        }
    }
}
