package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentParserRouterTest {

    private final DocumentParserRouter router = new DocumentParserRouter();

    @Test
    @DisplayName("Markdown 与 HTML 文件应进入原生解析路径")
    void route_shouldUseNativeRoutesForMarkdownAndHtml() {
        assertEquals(DocumentParseRoute.NATIVE_MARKDOWN, router.route("handoff.MD"));
        assertEquals(DocumentParseRoute.NATIVE_MARKDOWN, router.route("handoff.markdown"));
        assertEquals(DocumentParseRoute.NATIVE_HTML, router.route("page.HTML"));
        assertEquals(DocumentParseRoute.NATIVE_HTML, router.route("page.htm"));
    }

    @Test
    @DisplayName("未知扩展名应回退到 Tika 路径")
    void route_shouldFallbackToTikaForUnknownExtension() {
        assertEquals(DocumentParseRoute.TIKA, router.route("report.pdf"));
        assertEquals(DocumentParseRoute.TIKA, router.route("README"));
        assertEquals(DocumentParseRoute.TIKA, router.route(null));
    }

    @Test
    @DisplayName("文件扩展名提取应处理空值和大小写")
    void fileExtension_shouldNormalizeFilenameExtension() {
        assertEquals("md", DocumentParserRouter.fileExtension("A.MD"));
        assertEquals("bin", DocumentParserRouter.fileExtension("README"));
        assertEquals("bin", DocumentParserRouter.fileExtension("empty."));
        assertEquals("bin", DocumentParserRouter.fileExtension(" "));
    }
}
