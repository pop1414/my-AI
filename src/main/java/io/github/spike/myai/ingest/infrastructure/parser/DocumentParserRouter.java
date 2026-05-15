package io.github.spike.myai.ingest.infrastructure.parser;

import java.util.Locale;

/**
 * 根据文件名决定解析路径。
 */
final class DocumentParserRouter {

    DocumentParseRoute route(String filename) {
        String extension = fileExtension(filename);
        return switch (extension) {
            case "md", "markdown", "mdown", "mkd" -> DocumentParseRoute.NATIVE_MARKDOWN;
            case "html", "htm" -> DocumentParseRoute.NATIVE_HTML;
            default -> DocumentParseRoute.TIKA;
        };
    }

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
