package io.github.spike.myai.ingest.infrastructure.parser;

import io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException;
import java.util.Locale;

/**
 * 根据文件名后缀决定文档解析路径。
 *
 * <p>路由策略（Story 3.1 重构后）：
 * <ul>
 *   <li>全部 22 个支持的扩展名（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）→ {@link DocumentParseRoute#DOCLING}；</li>
 *   <li>其他扩展名、无扩展名、null/空白文件名 → 抛出 {@link UnsupportedDocumentFormatException}。</li>
 * </ul>
 *
 * <p>{@link #fileExtension(String)} 静态方法供 {@link DoclingDocumentParser}
 * 提取文件后缀，行为不受路由变更影响。
 *
 * @author spike
 * @since 1.0.0
 */
final class DocumentParserRouter {

    /**
     * 根据文件名后缀决定解析路径。
     *
     * @param filename 原始文件名（可为 null）
     * @return 解析路由（当前仅 {@link DocumentParseRoute#DOCLING}）
     * @throws UnsupportedDocumentFormatException 不支持的格式、null/空白文件名、无扩展名
     */
    DocumentParseRoute route(String filename) {
        if (filename == null) {
            throw new UnsupportedDocumentFormatException("null");
        }
        if (filename.isBlank()) {
            throw new UnsupportedDocumentFormatException("blank");
        }
        String extension = fileExtension(filename);
        return switch (extension) {
            case "pdf",
                 "doc", "docx",
                 "xls", "xlsx",
                 "ppt", "pptx",
                 "png", "jpg", "jpeg", "gif", "bmp", "tiff", "tif", "webp",
                 "md", "markdown", "mdown", "mkd",
                 "html", "htm",
                 "txt" -> DocumentParseRoute.DOCLING;
            default -> throw new UnsupportedDocumentFormatException(extension);
        };
    }

    /**
     * 提取文件名的小写扩展名。
     *
     * <p>null/空白文件名、无扩展名、扩展名为空时返回 {@code "bin"}。
     *
     * @param filename 原始文件名
     * @return 小写扩展名或 {@code "bin"}
     */
    static String fileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "bin";
        }
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
