package io.github.spike.myai.ingest.domain.model;

/**
 * chunk 内容类型。
 *
 * <p>由 Docling Serve 在解析阶段产出，标识该 chunk 对应的文档结构元素。
 * TXT 等无结构标记的格式默认使用 {@link #PARAGRAPH}。
 *
 * @author spike
 * @since 1.0.0
 */
public enum ChunkContentType {

    /** 普通段落文本。 */
    PARAGRAPH,

    /** 表格内容。 */
    TABLE,

    /** 列表项。 */
    LIST_ITEM,

    /** 代码块。 */
    CODE_BLOCK,

    /** 标题行。 */
    HEADING
}
