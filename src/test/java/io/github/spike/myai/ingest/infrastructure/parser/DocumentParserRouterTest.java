package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DocumentParserRouter 单元测试。
 *
 * <p>验证路由策略：22 个支持扩展名走 DOCLING，不支持格式抛出异常。
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

    // --- 图片格式 ---

    @Test
    @DisplayName("png 图片应路由到 Docling")
    void route_shouldRoutePngToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.png"));
    }

    @Test
    @DisplayName("jpg 图片应路由到 Docling")
    void route_shouldRouteJpgToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.jpg"));
    }

    @Test
    @DisplayName("jpeg 图片应路由到 Docling")
    void route_shouldRouteJpegToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.jpeg"));
    }

    @Test
    @DisplayName("gif 图片应路由到 Docling")
    void route_shouldRouteGifToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("icon.gif"));
    }

    @Test
    @DisplayName("bmp 图片应路由到 Docling")
    void route_shouldRouteBmpToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("image.bmp"));
    }

    @Test
    @DisplayName("tiff 图片应路由到 Docling")
    void route_shouldRouteTiffToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.tiff"));
    }

    @Test
    @DisplayName("tif 图片应路由到 Docling")
    void route_shouldRouteTifToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("scan.tif"));
    }

    @Test
    @DisplayName("webp 图片应路由到 Docling")
    void route_shouldRouteWebpToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("photo.webp"));
    }

    // --- Markdown 格式 ---

    @Test
    @DisplayName("md 扩展名应路由到 Docling")
    void route_shouldRouteMdToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("readme.md"));
    }

    @Test
    @DisplayName("大写 MD 扩展名应路由到 Docling")
    void route_shouldRouteUppercaseMdToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("guide.MD"));
    }

    @Test
    @DisplayName("markdown 扩展名应路由到 Docling")
    void route_shouldRouteMarkdownToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.markdown"));
    }

    @Test
    @DisplayName("mdown 扩展名应路由到 Docling")
    void route_shouldRouteMdownToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.mdown"));
    }

    @Test
    @DisplayName("mkd 扩展名应路由到 Docling")
    void route_shouldRouteMkdToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("doc.mkd"));
    }

    // --- HTML 格式 ---

    @Test
    @DisplayName("HTML 文件应路由到 Docling")
    void route_shouldRouteHtmlToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.html"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.HTML"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("page.htm"));
    }

    // --- TXT 格式 ---

    @Test
    @DisplayName("纯文本文件应路由到 Docling")
    void route_shouldRouteTxtToDocling() {
        assertEquals(DocumentParseRoute.DOCLING, router.route("notes.txt"));
        assertEquals(DocumentParseRoute.DOCLING, router.route("NOTES.TXT"));
    }

    // === 不支持格式 → 抛出异常 ===

    @Test
    @DisplayName("csv 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectCsv() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("data.csv"));
    }

    @Test
    @DisplayName("epub 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectEpub() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("book.epub"));
    }

    @Test
    @DisplayName("rtf 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectRtf() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("doc.rtf"));
    }

    @Test
    @DisplayName("json 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectJson() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("config.json"));
    }

    @Test
    @DisplayName("xml 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectXml() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("data.xml"));
    }

    @Test
    @DisplayName("zip 格式应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectZip() {
        assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("archive.zip"));
    }

    @Test
    @DisplayName("null 文件名应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectNullFilename() {
        UnsupportedDocumentFormatException ex =
                assertThrows(UnsupportedDocumentFormatException.class, () -> router.route(null));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    @DisplayName("空字符串文件名应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectEmptyFilename() {
        UnsupportedDocumentFormatException ex =
                assertThrows(UnsupportedDocumentFormatException.class, () -> router.route(""));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    @DisplayName("空白字符串文件名应抛出 UnsupportedDocumentFormatException")
    void route_shouldRejectWhitespaceFilename() {
        UnsupportedDocumentFormatException ex =
                assertThrows(UnsupportedDocumentFormatException.class, () -> router.route("   "));
        assertTrue(ex.getMessage().contains("blank"));
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
