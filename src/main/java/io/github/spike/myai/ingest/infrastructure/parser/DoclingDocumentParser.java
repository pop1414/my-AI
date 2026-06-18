package io.github.spike.myai.ingest.infrastructure.parser;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 基于 Docling Serve Convert API 的文档解析实现。
 *
 * <p>通过 Arconia 自动配置的 {@link DoclingServeApi} 调用 Docling Serve 的
 * {@code /v1/convert/source} 接口，仅负责“源文件 -> cleanedMarkdown”的转换职责；
 * 分块由 {@code DoclingDocumentChunker} 在后续独立完成。
 *
 * @author spike
 * @since 1.0.0
 */
@Component("docling")
public class DoclingDocumentParser implements DocumentTextParser {

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    /** 标题提取正则，用于构建 processingMetadata 中的 title_outline_sample。 */
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    /** title_outline_sample 最大保留条数。 */
    private static final int MAX_TITLE_OUTLINE_SIZE = 3;
    /** 输入文件最大字节数（50MB），避免 Base64 编码时占用过高内存。 */
    private static final int MAX_INPUT_BYTES = 50 * 1024 * 1024;

    private final DoclingServeApi doclingServeApi;
    private final ObjectMapper objectMapper;
    private final int maxTextLength;
    /** 文本清洗服务，对 Docling 输出 Markdown 执行最小破坏清洗。 */
    private final TextCleaningService textCleaningService;
    private final Clock clock;

    /**
     * 构造器注入：装配 Docling API 客户端、序列化器、清洗服务和 ingest 配置。
     *
     * @param doclingServeApi Docling Serve API 客户端
     * @param objectMapper JSON 序列化器
     * @param ingestProperties ingest 配置属性
     * @param textCleaningService 文本清洗服务
     */
    @Autowired
    public DoclingDocumentParser(
            DoclingServeApi doclingServeApi,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties,
            TextCleaningService textCleaningService) {
        this(doclingServeApi, objectMapper, ingestProperties, textCleaningService, Clock.systemUTC());
    }

    /** Package-private 构造器，供测试注入 Clock。 */
    DoclingDocumentParser(
            DoclingServeApi doclingServeApi,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties,
            TextCleaningService textCleaningService,
            Clock clock) {
        this.doclingServeApi = doclingServeApi;
        this.objectMapper = objectMapper;
        this.maxTextLength = ingestProperties.getParser().getMaxTextLength();
        this.textCleaningService = textCleaningService;
        this.clock = clock;
    }

    /**
     * 执行文档解析，将原始文件转换为 cleanedMarkdown。
     *
     * <p>解析链路：
     * <ol>
     *   <li>校验输入内容非空且大小在限制内；</li>
     *   <li>构造 {@link ConvertDocumentRequest} 并调用 {@link DoclingServeApi#convertSource(ConvertDocumentRequest)}；</li>
     *   <li>从 Convert 响应中提取可用文本内容；</li>
     *   <li>调用 {@link TextCleaningService#cleanNativeMarkdown(String)} 执行最小破坏清洗；</li>
     *   <li>校验清洗结果并映射为 {@link DocumentParseResult}。</li>
     * </ol>
     *
     * @param filename 原始文件名
     * @param content 文件字节数组
     * @return 解析结果，包含 cleanedMarkdown 与 processingMetadata
     * @throws IllegalStateException 输入为空、响应缺失正文或清洗结果非法时
     * @throws DoclingPermanentException Docling 返回不可重试的 4xx 时
     * @throws DoclingTransientException Docling 返回 5xx、超时或其他瞬时错误时
     */
    @Override
    public DocumentParseResult parse(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("filename must not be null or empty");
        }
        if (isMarkdownFilename(filename)) {
            return mapMarkdownSourceToParseResult(filename, content);
        }

        try {
            ConvertDocumentResponse response = callDoclingApi(filename, content);
            return mapToDocumentParseResult(filename, response);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (HttpClientErrorException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 408 || statusCode == 429) {
                throw new DoclingTransientException(
                        "docling returned transient client error: " + ex.getStatusCode(), ex);
            }
            throw new DoclingPermanentException(
                    "docling returned client error: " + ex.getStatusCode(),
                    statusCode,
                    ex);
        } catch (HttpServerErrorException ex) {
            throw new DoclingTransientException(
                    "docling returned server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new DoclingTransientException("docling connection failed", ex);
        } catch (Exception ex) {
            throw new DoclingTransientException("unexpected docling error", ex);
        }
    }

    /**
     * 构造 Convert 请求并调用 Docling Serve。
     *
     * @param filename 原始文件名
     * @param content 文件字节数组
     * @return Docling Convert 响应
     */
    private ConvertDocumentResponse callDoclingApi(String filename, byte[] content) {
        if (content.length > MAX_INPUT_BYTES) {
            throw new IllegalStateException("input file exceeds max size: " + content.length);
        }

        String base64Content = Base64.getEncoder().encodeToString(content);

        FileSource source = FileSource.builder()
                .base64String(base64Content)
                .filename(filename)
                .build();

        ConvertDocumentOptions convertOptions = buildConvertOptions();

        ConvertDocumentRequest request = ConvertDocumentRequest.builder()
                .source(source)
                .options(convertOptions)
                .target(InBodyTarget.builder().build())
                .build();

        log.debug("调用 Docling Serve Convert API, filename={}, contentLength={}", filename, content.length);
        return doclingServeApi.convertSource(request);
    }

    /**
     * 构建 Docling Convert 选项。
     *
     * <p>当前 reader.md 需要保留图片资源，因此显式选择 Embedded 图片导出模式；
     * cleaned.md 则依赖后续清洗规则移除图片链接与 data URI，避免污染 RAG 主链。
     *
     * <p>受限于当前 Java SDK 版本：
     * <ul>
     *   <li>{@code ocr_engine=auto} 未暴露为枚举，因此保持未设置，沿用服务端默认 auto；</li>
     *   <li>{@code pdf_backend=docling_parse} 未暴露为枚举，使用服务端兼容别名 {@code dlparse_v4} 对齐。</li>
     * </ul>
     *
     * @return 对齐 Docling Web UI 关键开关后的 Convert 选项
     */
    private ConvertDocumentOptions buildConvertOptions() {
        return ConvertDocumentOptions.builder()
                .toFormats(List.of(OutputFormat.MARKDOWN))
                .imageExportMode(ImageRefMode.EMBEDDED)
                .doOcr(true)
                .pipeline(ProcessingPipeline.STANDARD)
                .pdfBackend(PdfBackend.DLPARSE_V4)
                .tableMode(TableFormerMode.ACCURATE)
                .includeImages(true)
                .doTableStructure(true)
                .build();
    }

    /**
     * 将 Docling Convert 响应映射为 {@link DocumentParseResult}。
     *
     * @param filename 原始文件名
     * @param response Docling Convert 响应
     * @return 解析结果
     */
    private DocumentParseResult mapToDocumentParseResult(String filename, ConvertDocumentResponse response) {
        String readerMarkdown = extractReaderMarkdown(response);
        validateReaderMarkdown(readerMarkdown);
        String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(readerMarkdown);
        validateCleanedMarkdown(cleanedMarkdown);

        String processingMetadata = buildProcessingMetadata(filename, cleanedMarkdown);
        return new DocumentParseResult(readerMarkdown, cleanedMarkdown, processingMetadata);
    }

    /**
     * 直接将 Markdown 源文件映射为双轨正文产物。
     *
     * @param filename 原始文件名
     * @param content Markdown 源文件字节
     * @return 解析结果
     */
    private DocumentParseResult mapMarkdownSourceToParseResult(String filename, byte[] content) {
        String readerMarkdown = new String(content, StandardCharsets.UTF_8);
        validateReaderMarkdown(readerMarkdown);
        String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(readerMarkdown);
        validateCleanedMarkdown(cleanedMarkdown);
        String processingMetadata = buildProcessingMetadata(filename, cleanedMarkdown);
        return new DocumentParseResult(readerMarkdown, cleanedMarkdown, processingMetadata);
    }

    /**
     * 从 Convert 响应中提取 Markdown 正文。
     *
     * <p>仅接受 {@code md_content} 作为唯一内容源。
     * 当 md_content 为 null 或空白时直接抛出异常，不静默降级到其他格式
     * （Phase 0 调查确认 md_content 空值频率为零，降级链已无存在价值）。
     *
     * @param response Docling Convert 响应
     * @return Markdown 正文内容
     * @throws IllegalStateException 响应状态异常、document 为空或 md_content 为空时
     */
    private String extractReaderMarkdown(ConvertDocumentResponse response) {
        if (response == null) {
            throw new IllegalStateException("response must not be null");
        }

        String status = response.getStatus();
        if (status == null || "error".equalsIgnoreCase(status) || "failure".equalsIgnoreCase(status)) {
            String errorDetail = status != null ? formatResponseErrors(response.getErrors()) : "";
            throw new IllegalStateException(
                    "docling conversion failed (status=%s%s)".formatted(status, errorDetail));
        }

        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            log.warn("Docling 文档转换存在错误 (status={}): {}", status, response.getErrors());
        }

        DocumentResponse document = response.getDocument();
        if (document == null) {
            throw new IllegalStateException("docling response contains no document");
        }

        String mdContent = document.getMarkdownContent();
        if (mdContent != null && !mdContent.isBlank()) {
            return mdContent;
        }

        throw new IllegalStateException(
                "docling response md_content is null/empty (status=%s)".formatted(status));
    }

    /**
     * 构建 processingMetadata JSON。
     *
     * @param filename 原始文件名
     * @param cleanedMarkdown 清洗后的 Markdown
     * @return 序列化后的 processingMetadata JSON
     */
    private String buildProcessingMetadata(String filename, String cleanedMarkdown) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "v1");
        root.put("stable", buildStableMetadata(filename));

        Map<String, Object> conditional = buildConditionalMetadata(cleanedMarkdown);
        if (!conditional.isEmpty()) {
            root.put("conditional", conditional);
        }

        root.put("best_effort", Map.of());

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize processing metadata", ex);
        }
    }

    private Map<String, Object> buildStableMetadata(String filename) {
        Map<String, Object> stable = new LinkedHashMap<>();
        stable.put("source_file", filename);
        stable.put("file_ext", DocumentParserRouter.fileExtension(filename));
        stable.put("mime_type", "application/octet-stream");
        stable.put("quality", "high");
        stable.put("created_at", clock.instant().toString());
        return stable;
    }

    private Map<String, Object> buildConditionalMetadata(String cleanedMarkdown) {
        Map<String, Object> conditional = new LinkedHashMap<>();

        List<String> titleOutlineSample = extractTitleOutlineSample(cleanedMarkdown);
        if (!titleOutlineSample.isEmpty()) {
            conditional.put("primary_title", titleOutlineSample.getFirst());
            conditional.put("title_outline_sample", titleOutlineSample);
        }

        return conditional;
    }

    /**
     * 校验清洗后的 Markdown 内容有效性。
     *
     * @param cleanedMarkdown 清洗后的 Markdown
     */
    private void validateReaderMarkdown(String readerMarkdown) {
        if (readerMarkdown == null || readerMarkdown.isBlank()) {
            throw new IllegalStateException("reader markdown is empty");
        }
    }

    private void validateCleanedMarkdown(String cleanedMarkdown) {
        if (cleanedMarkdown.isBlank()) {
            throw new IllegalStateException("parsed text is empty");
        }
        if (cleanedMarkdown.length() > maxTextLength) {
            throw new IllegalStateException("parsed text exceeds max length");
        }
    }

    private static String formatResponseErrors(List<ErrorItem> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        String details = errors.stream()
                .filter(Objects::nonNull)
                .map(error -> error.getComponentType() + ": " + error.getErrorMessage())
                .collect(Collectors.joining("; "));
        return ", errors=[" + details + "]";
    }

    private static List<String> extractTitleOutlineSample(String markdown) {
        Matcher matcher = MARKDOWN_HEADING.matcher(markdown);
        List<String> headings = new ArrayList<>();
        while (matcher.find() && headings.size() < MAX_TITLE_OUTLINE_SIZE) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    private static boolean isMarkdownFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        return normalizedFilename.endsWith(".md")
                || normalizedFilename.endsWith(".markdown")
                || normalizedFilename.endsWith(".mdown")
                || normalizedFilename.endsWith(".mkd");
    }
}
