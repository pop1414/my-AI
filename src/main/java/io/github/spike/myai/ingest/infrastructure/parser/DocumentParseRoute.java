package io.github.spike.myai.ingest.infrastructure.parser;

/**
 * 文档解析路由枚举，标识文件应进入的解析路径。
 *
 * <p>路由策略（Story 3.1 重构后）：
 * <ul>
 *   <li>{@link #DOCLING} — 全部 8 种支持格式（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）走 Docling Serve 解析；</li>
 *   <li>{@link #REJECT} — 不支持的格式（CSV/EPUB/RTF 等），由 {@link DocumentParserRouter} 抛出
 *       {@link io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException}。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
enum DocumentParseRoute {

    /** 全部 8 种支持格式走 DoclingDocumentParser */
    DOCLING,

    /** 不支持的格式（由 route() 抛异常，枚举值保留用于文档目的） */
    REJECT
}
