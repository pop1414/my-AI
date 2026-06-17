package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DocumentParserRouter 单元测试。
 *
 * <p>验证路由策略：全部 8 种支持格式走 DOCLING，不支持格式抛出异常。
 */
class DocumentParserRouterTest {

    private final DocumentParserRouter router = new DocumentParserRouter();

    // === 支持格式 → DOCLING ===

    @Test
    @DisplayName("PDF 文档应路由到 Docling")
    void route_shouldRoutePdfToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("report.pdf"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("REPORT.PDF"));
    }

    @Test
    @DisplayName("Word 文档应路由到 Docling")
    void route_shouldRouteDocToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.doc"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.docx"));
    }

    @Test
    @DisplayName("Excel 电子表格应路由到 Docling")
    void route_shouldRouteXlsToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("data.xls"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("data.xlsx"));
    }

    @Test
    @DisplayName("PowerPoint 演示文稿应路由到 Docling")
    void route_shouldRoutePptToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("slides.ppt"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("slides.pptx"));
    }

    @Test
    @DisplayName("图片文件应路由到 Docling（OCR）")
    void route_shouldRouteImageToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.png"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.jpg"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.jpeg"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("icon.gif"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("image.bmp"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.tiff"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.tif"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.webp"));
    }

    @Test
    @DisplayName("Markdown 文件应路由到 Docling")
    void route_shouldRouteMarkdownToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("readme.md"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("guide.MD"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.markdown"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.mdown"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.mkd"));
    }

    @Test
    @DisplayName("HTML 文件应路由到 Docling")
    void route_shouldRouteHtmlToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.html"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.HTML"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.htm"));
    }

    @Test
    @DisplayName("纯文本文件应路由到 Docling")
    void route_shouldRouteTxtToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("notes.txt"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("NOTES.TXT"));
    }

    // === 不支持格式 → 抛出异常 ===

    @Test
    @DisplayName("不支持的格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectUnsupportedFormat() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("data.csv"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("book.epub"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("doc.rtf"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("config.json"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("data.xml"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("archive.zip"));
    }

    @Test
    @DisplayName("null 文件名应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectNullFilename() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route(null));
    }

    @Test
    @DisplayName("空白文件名应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectBlankFilename() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route(""));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("   "));
    }

    @Test
    @DisplayName("无扩展名文件应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectNoExtension() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("README"));
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("empty."));
    }

    // === fileExtension 边界（不变） ===

    @Test
    @DisplayName("文件扩展名提取应处理空值和大小写")
    void fileExtension_shouldNormalizeFilenameExtension() {
        assertEquals("md", DocumentParserRouter.fileExtension("A.MD"));
        assertEquals("bin", DocumentParserRouter.fileExtension("README"));
        assertEquals("bin", DocumentParserRouter.fileExtension("empty."));
        assertEquals("bin", DocumentParserRouter.fileExtension(" "));
    }
}
