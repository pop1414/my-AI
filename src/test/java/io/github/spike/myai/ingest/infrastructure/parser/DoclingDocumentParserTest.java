package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ImageRefMode;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.options.PdfBackend;
import ai.docling.serve.api.convert.request.options.ProcessingPipeline;
import ai.docling.serve.api.convert.request.options.TableFormerMode;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.ErrorItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * DoclingDocumentParser 单元测试。
 */
class DoclingDocumentParserTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC);

    private DoclingServeApi doclingServeApi;
    private TextCleaningService textCleaningService;
    private SimpleMeterRegistry meterRegistry;
    private DoclingDocumentParser parser;

    @BeforeEach
    void setUp() {
        doclingServeApi = org.mockito.Mockito.mock(DoclingServeApi.class);
        textCleaningService = org.mockito.Mockito.mock(TextCleaningService.class);
        when(textCleaningService.cleanNativeMarkdown(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        meterRegistry = new SimpleMeterRegistry();

        IngestProperties properties = new IngestProperties();
        parser = new DoclingDocumentParser(
                doclingServeApi,
                new ObjectMapper(),
                properties,
                textCleaningService,
                FIXED_CLOCK,
                meterRegistry);
    }

    @Test
    @DisplayName("应将 Convert 响应映射为 DocumentParseResult")
    void parse_shouldReturnParseResult_whenValidDocument() throws Exception {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("test.pdf")
                .markdownContent("# 标题\n\n正文内容")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("test.pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        assertEquals("# 标题\n\n正文内容", result.readerMarkdown());
        assertEquals("# 标题\n\n正文内容", result.cleanedMarkdown());

        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("v1", metadata.path("schema_version").asText());
        assertEquals("test.pdf", metadata.path("stable").path("source_file").asText());
        assertEquals("pdf", metadata.path("stable").path("file_ext").asText());
        assertEquals("2026-06-16T12:00:00Z", metadata.path("stable").path("created_at").asText());
    }

    @Test
    @DisplayName("应正确提取 processingMetadata 中的 title_outline_sample")
    void parse_shouldExtractTitleOutlineSample_whenMarkdownHasHeadings() throws Exception {
        String markdown = "# 主标题\n\n## 节一\n\n正文\n\n## 节二\n\n正文";
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("doc.pdf")
                .markdownContent(markdown)
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("doc.pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("主标题", metadata.path("conditional").path("primary_title").asText());
        assertEquals(3, metadata.path("conditional").path("title_outline_sample").size());
        assertEquals("主标题", metadata.path("conditional").path("title_outline_sample").get(0).asText());
        assertEquals("节一", metadata.path("conditional").path("title_outline_sample").get(1).asText());
        assertEquals("节二", metadata.path("conditional").path("title_outline_sample").get(2).asText());
    }

    @Test
    @DisplayName("Markdown 源文件应直接保留原始正文并跳过 Docling 转换")
    void parse_shouldKeepSourceMarkdown_whenFilenameIsMarkdown() throws Exception {
        String sourceMarkdown = "# 标题\r\n\r\n![image](https://cdn.example.com/demo.png)\r\n\r\n正文";
        String cleanedMarkdown = "# 标题\n\n![image](https://cdn.example.com/demo.png)\n\n正文";
        when(textCleaningService.cleanNativeMarkdown(sourceMarkdown)).thenReturn(cleanedMarkdown);

        DocumentParseResult result = parser.parse("source.md", sourceMarkdown.getBytes(StandardCharsets.UTF_8));

        assertEquals(sourceMarkdown, result.readerMarkdown());
        assertEquals(cleanedMarkdown, result.cleanedMarkdown());
        JsonNode metadata = new ObjectMapper().readTree(result.processingMetadata());
        assertEquals("source.md", metadata.path("stable").path("source_file").asText());
        verify(doclingServeApi, never()).convertSource(any());
    }

    @Test
    @DisplayName("空内容应抛出 IllegalStateException")
    void parse_shouldThrowException_whenContentEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("empty.txt", new byte[0]));
        assertEquals("empty source content", ex.getMessage());
    }

    @Test
    @DisplayName("null 内容应抛出 IllegalStateException")
    void parse_shouldThrowException_whenContentNull() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("null.txt", null));
        assertEquals("empty source content", ex.getMessage());
    }

    @Test
    @DisplayName("null 文件名应抛出 IllegalStateException")
    void parse_shouldThrowException_whenFilenameNull() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse(null, "data".getBytes(StandardCharsets.UTF_8)));
        assertEquals("filename must not be null or empty", ex.getMessage());
    }

    @Test
    @DisplayName("空字符串文件名应抛出 IllegalStateException")
    void parse_shouldThrowException_whenFilenameEmpty() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("", "data".getBytes(StandardCharsets.UTF_8)));
        assertEquals("filename must not be null or empty", ex.getMessage());
    }

    @Test
    @DisplayName("纯空白文件名应抛出 IllegalStateException")
    void parse_shouldThrowException_whenFilenameBlank() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("   ", "data".getBytes(StandardCharsets.UTF_8)));
        assertEquals("filename must not be null or empty", ex.getMessage());
    }

    @Test
    @DisplayName("未知 Runtime 异常应包装为 DoclingTransientException")
    void parse_shouldThrowTransientException_whenUnknownRuntimeException() {
        RuntimeException apiError = new RuntimeException("connection refused");
        when(doclingServeApi.convertSource(any())).thenThrow(apiError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("error.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("unexpected docling error", ex.getMessage());
        assertEquals(apiError, ex.getCause());
    }

    @Test
    @DisplayName("Docling 4xx 错误应映射为 DoclingPermanentException")
    void parse_shouldThrowPermanentException_when4xxError() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Bad Request",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingPermanentException ex = assertThrows(
                DoclingPermanentException.class,
                () -> parser.parse("bad.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(400, ex.httpStatusCode());
        assertEquals(httpError, ex.getCause());
        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    @DisplayName("Docling 408 错误应映射为 DoclingTransientException")
    void parse_shouldThrowTransientException_when408Error() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.REQUEST_TIMEOUT,
                "Request Timeout",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("timeout408.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(httpError, ex.getCause());
        assertTrue(ex.getMessage().contains("408"));
    }

    @Test
    @DisplayName("Docling 429 错误应映射为 DoclingTransientException")
    void parse_shouldThrowTransientException_when429Error() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("ratelimit429.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(httpError, ex.getCause());
        assertTrue(ex.getMessage().contains("429"));
    }

    @Test
    @DisplayName("Docling 422 错误应映射为 DoclingPermanentException")
    void parse_shouldThrowPermanentException_when422Error() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingPermanentException ex = assertThrows(
                DoclingPermanentException.class,
                () -> parser.parse("unprocessable.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(422, ex.httpStatusCode());
        assertTrue(ex.getMessage().contains("422"));
    }

    @Test
    @DisplayName("Docling 5xx 错误应映射为 DoclingTransientException")
    void parse_shouldThrowTransientException_when5xxError() {
        var httpError = org.springframework.web.client.HttpServerErrorException.create(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("server-error.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(httpError, ex.getCause());
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    @DisplayName("Docling 503 错误应映射为 DoclingTransientException")
    void parse_shouldThrowTransientException_when503Error() {
        var httpError = org.springframework.web.client.HttpServerErrorException.create(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("unavailable.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals(httpError, ex.getCause());
        assertTrue(ex.getMessage().contains("503"));
    }

    @Test
    @DisplayName("连接超时应映射为 DoclingTransientException")
    void parse_shouldThrowTransientException_whenConnectionTimeout() {
        var timeoutError = new org.springframework.web.client.ResourceAccessException(
                "I/O error", new java.net.SocketTimeoutException("connect timed out"));
        when(doclingServeApi.convertSource(any())).thenThrow(timeoutError);

        DoclingTransientException ex = assertThrows(
                DoclingTransientException.class,
                () -> parser.parse("timeout.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling connection failed", ex.getMessage());
        assertEquals(timeoutError, ex.getCause());
    }

    @Test
    @DisplayName("document 为空时应抛出 IllegalStateException")
    void parse_shouldThrowException_whenDocumentNull() {
        ConvertDocumentResponse response = ConvertDocumentResponse.builder()
                .status("success")
                .build();
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("empty-doc.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling response contains no document", ex.getMessage());
    }

    @Test
    @DisplayName("所有内容格式均为空时应抛出含 md_content 信息的 IllegalStateException")
    void parse_shouldThrowException_whenAllContentFormatsNull() {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("null-md.pdf")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("null-md.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getMessage().contains("md_content"), "异常消息应包含 md_content 关键字");
    }

    @Test
    @DisplayName("md_content 为 null 时应抛出 IllegalStateException（不降级）")
    void parse_shouldThrowException_whenMarkdownContentNull() {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("no-md.pdf")
                .htmlContent("<h1>Title</h1><p>正文</p>")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("no-md.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getMessage().contains("md_content"), "异常消息应包含 md_content 关键字");
    }

    @Test
    @DisplayName("md_content 为空白字符串时应抛出 IllegalStateException")
    void parse_shouldThrowException_whenMarkdownContentBlank() {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("blank-md.pdf")
                .markdownContent("   \n  ")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("blank-md.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertTrue(ex.getMessage().contains("md_content"), "异常消息应包含 md_content 关键字");
    }

    @Test
    @DisplayName("response status 为 error 时应抛出 IllegalStateException")
    void parse_shouldThrowException_whenResponseStatusError() {
        ConvertDocumentResponse response = ConvertDocumentResponse.builder()
                .document(DocumentResponse.builder()
                        .filename("error-status.pdf")
                        .markdownContent("partial content")
                        .build())
                .status("error")
                .build();
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("error-status.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling conversion failed (status=error)", ex.getMessage());
    }

    @Test
    @DisplayName("response status 为 failure 时应抛出包含错误详情的异常")
    void parse_shouldThrowExceptionWithStatusDetail_whenResponseStatusFailure() {
        ErrorItem errorItem = org.mockito.Mockito.mock(ErrorItem.class);
        when(errorItem.getComponentType()).thenReturn("pdf_backend");
        when(errorItem.getErrorMessage()).thenReturn("unsupported PDF version");

        ConvertDocumentResponse response = ConvertDocumentResponse.builder()
                .document(DocumentResponse.builder().filename("failure.pdf").build())
                .status("failure")
                .errors(List.of(errorItem))
                .build();
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("failure.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("docling conversion failed (status=failure, errors=[pdf_backend: unsupported PDF version])",
                ex.getMessage());
    }

    @Test
    @DisplayName("response 含 errors 但 markdown 有效时应解析成功")
    void parse_shouldSucceedWithWarning_whenResponseHasErrors() {
        ErrorItem errorItem = org.mockito.Mockito.mock(ErrorItem.class);
        ConvertDocumentResponse response = ConvertDocumentResponse.builder()
                .document(DocumentResponse.builder()
                        .filename("partial.pdf")
                        .markdownContent("# Valid Markdown")
                        .build())
                .status("partial_success")
                .errors(List.of(errorItem))
                .build();
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("partial.pdf", "data".getBytes(StandardCharsets.UTF_8));

        assertEquals("# Valid Markdown", result.cleanedMarkdown());
    }

    @Test
    @DisplayName("输入字节数超过上限时应抛出 IllegalStateException")
    void parse_shouldThrowException_whenInputExceedsMaxBytes() {
        byte[] largeContent = new byte[50 * 1024 * 1024 + 1];

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> parser.parse("large.pdf", largeContent));

        assertTrue(ex.getMessage().startsWith("input file exceeds max size"));
    }

    @Test
    @DisplayName("超过最大文本长度时应抛出 IllegalStateException")
    void parse_shouldValidateMaxLength_whenExceedThreshold() {
        IngestProperties shortLimit = new IngestProperties();
        shortLimit.getParser().setMaxTextLength(10);
        DoclingDocumentParser shortParser = new DoclingDocumentParser(
                doclingServeApi, new ObjectMapper(), shortLimit, textCleaningService, FIXED_CLOCK, meterRegistry);

        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("long.pdf")
                .markdownContent("a".repeat(20))
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> shortParser.parse("long.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        assertEquals("parsed text exceeds max length", ex.getMessage());
    }

    @Test
    @DisplayName("应正确构造 ConvertDocumentRequest")
    void parse_shouldBuildCorrectRequest_whenCalled() {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("test.pdf")
                .markdownContent("内容")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        parser.parse("test.pdf", "hello".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ConvertDocumentRequest> captor =
                ArgumentCaptor.forClass(ConvertDocumentRequest.class);
        verify(doclingServeApi).convertSource(captor.capture());

        ConvertDocumentRequest request = captor.getValue();
        assertEquals(1, request.getSources().size());
        assertTrue(request.getSources().getFirst() instanceof FileSource);
        FileSource source = (FileSource) request.getSources().getFirst();
        assertEquals("test.pdf", source.getFilename());
        assertEquals(List.of(
                OutputFormat.MARKDOWN), request.getOptions().getToFormats());
        assertEquals(ImageRefMode.EMBEDDED, request.getOptions().getImageExportMode());
        assertEquals(Boolean.TRUE, request.getOptions().getDoOcr());
        assertEquals(ProcessingPipeline.STANDARD, request.getOptions().getPipeline());
        assertEquals(PdfBackend.DLPARSE_V4, request.getOptions().getPdfBackend());
        assertEquals(TableFormerMode.ACCURATE, request.getOptions().getTableMode());
        assertEquals(Boolean.TRUE, request.getOptions().getIncludeImages());
        assertEquals(Boolean.TRUE, request.getOptions().getDoTableStructure());
        assertNotNull(request.getTarget());
        assertTrue(request.getTarget() instanceof InBodyTarget);
    }

    @Test
    @DisplayName("应调用 cleanNativeMarkdown 清洗 Docling 原始输出")
    void parse_shouldCleanNativeMarkdownBeforeValidation() {
        String rawDoclingMarkdown = "# 标题\n\n\r\n正文\r\n\r\n\r\n多余空行";
        String cleanedMarkdown = "# 标题\n\n正文\n\n多余空行";
        when(textCleaningService.cleanNativeMarkdown(rawDoclingMarkdown)).thenReturn(cleanedMarkdown);

        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("test.pdf")
                .markdownContent(rawDoclingMarkdown)
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        DocumentParseResult result = parser.parse("test.pdf", "data".getBytes(StandardCharsets.UTF_8));

        assertEquals(rawDoclingMarkdown, result.readerMarkdown());
        assertEquals(cleanedMarkdown, result.cleanedMarkdown());
        verify(textCleaningService).cleanNativeMarkdown(rawDoclingMarkdown);
    }

    @Test
    @DisplayName("应将 Docling 原始 Markdown 传给 TextCleaningService")
    void parse_shouldPassRawMarkdownToCleaningService() {
        String rawDoclingMarkdown = "## 原始内容\n\n含控制字符和 BOM";
        when(textCleaningService.cleanNativeMarkdown(any())).thenReturn("清洗后内容");

        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("test.pdf")
                .markdownContent(rawDoclingMarkdown)
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        parser.parse("test.pdf", "data".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(textCleaningService).cleanNativeMarkdown(captor.capture());
        assertEquals(rawDoclingMarkdown, captor.getValue());
    }

    // === 指标埋点验证 ===

    @Test
    @DisplayName("解析成功应记录 parse.duration 指标（Timer），标签 format=pdf")
    void parse_shouldRecordParseDuration_whenSuccessful() {
        ConvertDocumentResponse response = buildResponse(DocumentResponse.builder()
                .filename("test.pdf")
                .markdownContent("内容")
                .build());
        when(doclingServeApi.convertSource(any())).thenReturn(response);

        parser.parse("test.pdf", "dummy".getBytes(StandardCharsets.UTF_8));

        Timer timer = meterRegistry.find("docling.parse.duration").tag("format", "pdf").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    @DisplayName("Docling 永久错误应记录 parse.errors 指标（errorType=permanent）")
    void parse_shouldRecordParseErrorPermanent_whenDoclingPermanentException() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Bad Request",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        assertThrows(DoclingPermanentException.class,
                () -> parser.parse("bad.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        Counter counter = meterRegistry.find("docling.parse.errors").tag("errorType", "permanent").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("Docling 瞬时错误应记录 parse.errors 指标（errorType=transient）")
    void parse_shouldRecordParseErrorTransient_whenDoclingTransientException() {
        var httpError = org.springframework.web.client.HttpServerErrorException.create(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        assertThrows(DoclingTransientException.class,
                () -> parser.parse("error.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        Counter counter = meterRegistry.find("docling.parse.errors").tag("errorType", "transient").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("Docling 408 超时应记录 parse.errors 指标（errorType=transient）")
    void parse_shouldRecordParseErrorTransient_when408Timeout() {
        var httpError = org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.REQUEST_TIMEOUT,
                "Request Timeout",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8);
        when(doclingServeApi.convertSource(any())).thenThrow(httpError);

        assertThrows(DoclingTransientException.class,
                () -> parser.parse("timeout.pdf", "data".getBytes(StandardCharsets.UTF_8)));

        Counter counter = meterRegistry.find("docling.parse.errors").tag("errorType", "transient").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("Markdown 短路路径应记录 parse.duration（format=md），不记录 parse.errors")
    void parse_shouldRecordParseDurationForMarkdownPath_whenMarkdownFile() {
        String sourceMarkdown = "# 标题\n\n正文";
        when(textCleaningService.cleanNativeMarkdown(sourceMarkdown)).thenReturn(sourceMarkdown);

        parser.parse("source.md", sourceMarkdown.getBytes(StandardCharsets.UTF_8));

        Timer timer = meterRegistry.find("docling.parse.duration").tag("format", "md").timer();
        assertNotNull(timer, "Markdown 短路路径应记录 parse.duration (format=md)");
        assertEquals(1, timer.count());

        Counter permanentCounter = meterRegistry.find("docling.parse.errors").tag("errorType", "permanent").counter();
        Counter transientCounter = meterRegistry.find("docling.parse.errors").tag("errorType", "transient").counter();
        assertEquals(0.0, permanentCounter.count(), "Markdown 短路路径不应记录 parse.errors (permanent)");
        assertEquals(0.0, transientCounter.count(), "Markdown 短路路径不应记录 parse.errors (transient)");
    }

    private ConvertDocumentResponse buildResponse(DocumentResponse document) {
        return ConvertDocumentResponse.builder()
                .document(document)
                .status("success")
                .processingTime(0.1)
                .build();
    }
}
