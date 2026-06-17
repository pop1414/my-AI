package io.github.spike.myai.ingest.infrastructure.parser;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.chunk.response.Document;
import ai.docling.serve.api.chunk.response.ExportDocumentResponse;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.response.ErrorItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
 * 基于 Docling Serve 的文档解析实现。
 *
 * <p>通过 Arconia 自动配置的 {@link DoclingServeApi} 调用 Docling Serve 的
 * HybridChunker 接口，一步完成文档转换和分块。所有 Docling 特定逻辑
 * （Base64 编码、请求构造、响应映射）封装在此 adapter 内，
 * {@link DoclingServeApi} 的类型不暴露到本类以外。
 *
 * @author spike
 * @since 1.0.0
 */
@Component("docling")
public class DoclingDocumentParser implements DocumentTextParser {

    // === Constants ===

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentParser.class);

    /** HybridChunker 单块最大 token 数，对齐论文最优 faithfulness 97.59 */
    private static final int DEFAULT_MAX_TOKENS = 512;

    /** 合并过小块，对齐论文最优 faithfulness */
    private static final boolean DEFAULT_MERGE_PEERS = true;

    /** Markdown heading 正则，用于提取 title_outline_sample */
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");

    /** title_outline_sample 最大条目数 */
    private static final int MAX_TITLE_OUTLINE_SIZE = 3;

    /** 输入文件最大字节数（50MB），防止 Base64 编码时 OOM */
    private static final int MAX_INPUT_BYTES = 50 * 1024 * 1024;

    // === Instance Fields ===

    private final DoclingServeApi doclingServeApi;
    private final ObjectMapper objectMapper;
    private final int maxTextLength;
    /** 文本清洗服务，对 Docling 产出 Markdown 执行最小破坏清洗 */
    private final TextCleaningService textCleaningService;
    private final Clock clock;

    // === Constructors ===

    /**
     * 构造器注入：装配 Docling API 客户端、序列化器、清洗服务和配置参数。
     *
     * @param doclingServeApi    Docling Serve API 客户端（Arconia 自动配置注入）
     * @param objectMapper       JSON 序列化器
     * @param ingestProperties   ingest 管道配置属性
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

    // === Public API ===

    /**
     * 执行文档解析，通过 Docling Serve 的 HybridChunker 一步完成转换和分块。
     *
     * <p>解析链路：
     * <ol>
     *   <li>校验输入内容非空；</li>
     *   <li>Base64 编码后构造 {@link FileSource}；</li>
     *   <li>调用 {@link DoclingServeApi#chunkSourceWithHybridChunker}；</li>
     *   <li>从响应提取原始 Markdown；</li>
     *   <li>调用 {@link TextCleaningService#cleanNativeMarkdown} 执行最小破坏清洗；</li>
     *   <li>校验清洗结果并映射为 {@link DocumentParseResult}。</li>
     * </ol>
     *
     * <p>异常策略：
     * <ul>
     *   <li>输入/响应校验失败 → {@link IllegalStateException}（不可重试的逻辑错误）</li>
     *   <li>Docling 4xx → {@link DoclingPermanentException}（永久失败，不重试）</li>
     *   <li>Docling 5xx / 超时 / 网络 → {@link DoclingTransientException}（瞬时错误，可重试）</li>
     *   <li>其他未知异常 → {@link DoclingTransientException}（保守策略：先尝试重试）</li>
     * </ul>
     *
     * @param filename 原始文件名
     * @param content  文件字节数组
     * @return 解析结果（cleanedMarkdown + processingMetadata）
     * @throws IllegalStateException           内容为空或响应校验失败时
     * @throws DoclingPermanentException       Docling 4xx 客户端错误时
     * @throws DoclingTransientException       Docling 5xx / 超时 / 网络错误时
     */
    @Override
    public DocumentParseResult parse(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }

        try {
            ChunkDocumentResponse response = callDoclingApi(filename, content);
            return mapToDocumentParseResult(filename, response);
        } catch (IllegalStateException ex) {
            // 输入校验/响应校验错误（空内容、空 documents、null markdownContent、超长文本）
            // 这些是逻辑错误，不可重试
            throw ex;
        } catch (HttpClientErrorException ex) {
            int statusCode = ex.getStatusCode().value();
            // 408/429 是瞬时性客户端错误，应按瞬时处理（可重试）
            if (statusCode == 408 || statusCode == 429) {
                throw new DoclingTransientException(
                        "docling returned transient client error: " + ex.getStatusCode(), ex);
            }
            // 其余 4xx → 永久失败（不重试）
            throw new DoclingPermanentException(
                    "docling returned client error: " + ex.getStatusCode(),
                    statusCode,
                    ex);
        } catch (HttpServerErrorException ex) {
            // 5xx 服务端错误 → 瞬时错误（可重试）
            throw new DoclingTransientException(
                    "docling returned server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            // 连接超时 / 读超时 → 瞬时错误（可重试）
            throw new DoclingTransientException("docling connection failed", ex);
        } catch (Exception ex) {
            // 其他未知异常 → 保守策略：视为瞬时错误（先尝试重试）
            throw new DoclingTransientException("unexpected docling error", ex);
        }
    }

    // === Docling API Call ===

    /**
     * 构造请求并调用 Docling Serve 的 HybridChunker 接口。
     *
     * @param filename 原始文件名
     * @param content  文件字节数组
     * @return Docling 响应
     */
    private ChunkDocumentResponse callDoclingApi(String filename, byte[] content) {
        if (content.length > MAX_INPUT_BYTES) {
            throw new IllegalStateException("input file exceeds max size: " + content.length);
        }

        String base64Content = Base64.getEncoder().encodeToString(content);

        FileSource source = FileSource.builder()
                .base64String(base64Content)
                .filename(filename)
                .build();

        ConvertDocumentOptions convertOptions = ConvertDocumentOptions.builder()
                .toFormats(List.of(OutputFormat.MARKDOWN, OutputFormat.HTML,
                        OutputFormat.TEXT, OutputFormat.DOCTAGS))
                .build();

        HybridChunkerOptions chunkerOptions = HybridChunkerOptions.builder()
                .maxTokens(DEFAULT_MAX_TOKENS)
                .mergePeers(DEFAULT_MERGE_PEERS)
                .build();

        HybridChunkDocumentRequest request = HybridChunkDocumentRequest.builder()
                .source(source)
                .options(convertOptions)
                .includeConvertedDoc(true)
                .chunkingOptions(chunkerOptions)
                .build();

        log.debug("调用 Docling Serve HybridChunker, filename={}, contentLength={}", filename, content.length);
        return doclingServeApi.chunkSourceWithHybridChunker(request);
    }

    // === Response Mapping ===

    /**
     * 将 Docling 响应映射为 {@link DocumentParseResult}。
     *
     * <p>先从响应提取原始 Markdown，再调用 {@link TextCleaningService#cleanNativeMarkdown}
     * 执行统一换行符、去除控制字符、压缩连续空行等最小破坏清洗，最后校验并映射。
     */
    private DocumentParseResult mapToDocumentParseResult(String filename, ChunkDocumentResponse response) {
        String rawMarkdown = extractCleanedMarkdown(response);
        String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(rawMarkdown);
        validateCleanedMarkdown(cleanedMarkdown);

        String processingMetadata = buildProcessingMetadata(filename, cleanedMarkdown);
        return new DocumentParseResult(cleanedMarkdown, processingMetadata);
    }

    /**
     * 从 Docling 响应中提取可用内容，支持多格式降级。
     *
     <p>提取优先级：md_content → html_content（转 Markdown）→ text_content
     * → doctags_content → chunks 文本拼接。
     * Docling Serve 的 md_content 取决于服务器转换器配置，
     * 部分部署可能只有 html_content、text_content 或 doctags_content。
     * 当所有内容格式均不可用时，尝试从 HybridChunker 产出的 chunks
     * 列表中拼接文本（这是 HybridChunker 的主输出）。
     *
     * @param response Docling chunk 响应（不可为 null）
     * @return 可用于后续清洗的文本内容
     * @throws IllegalStateException 当 documents 列表为空时
     * @throws IllegalStateException 当文档 status 为 error/failure 时（含 errors 详情）
     * @throws IllegalStateException 当所有内容格式（md/html/text）均为 null 时
     */
    private String extractCleanedMarkdown(ChunkDocumentResponse response) {
        java.util.Objects.requireNonNull(response, "response must not be null");
        if (response.getDocuments() == null || response.getDocuments().isEmpty()) {
            throw new IllegalStateException("docling response contains no documents");
        }

        Document document = response.getDocuments().getFirst();

        // status 检查优先：转换失败时内容字段为 null 是预期行为，
        // 必须先报告真正的失败原因（status + errors 详情），而非含糊的 "no content"
        String status = document.getStatus();
        if ("error".equalsIgnoreCase(status) || "failure".equalsIgnoreCase(status)) {
            String errorDetail = formatDocumentErrors(document);
            throw new IllegalStateException(
                    "docling conversion failed (status=%s%s)".formatted(status, errorDetail));
        }

        if (document.getErrors() != null && !document.getErrors().isEmpty()) {
            log.warn("Docling 文档转换存在错误 (status={}): {}", status, document.getErrors());
        }

        // 多格式降级：md_content → html_content → text_content → doctags_content → chunks 文本
        // Docling Serve 的 md_content 取决于服务器转换器配置，不一定所有部署都有
        if (document.getContent() != null) {
            ExportDocumentResponse content = document.getContent();
            if (content.getMarkdownContent() != null) {
                return content.getMarkdownContent();
            }
            if (content.getHtmlContent() != null) {
                log.info("Docling md_content 为空，降级使用 html_content (status={})", status);
                return content.getHtmlContent();
            }
            if (content.getTextContent() != null) {
                log.info("Docling md_content/html_content 均为空，降级使用 text_content (status={})", status);
                return content.getTextContent();
            }
            if (content.getDoctagsContent() != null) {
                log.info("Docling md/html/text_content 均为空，降级使用 doctags_content (status={})", status);
                return content.getDoctagsContent();
            }
        }

        // Docling 响应的 chunks 是 HybridChunker 主输出，即使 document content 为空也可能有 chunk 文本
        String fallbackFromChunks = extractTextFromChunks(response);
        if (fallbackFromChunks != null) {
            log.info("Docling 所有 content 格式均为空，降级使用 chunks 文本 (status={}, chunks={})",
                    status, response.getChunks().size());
            return fallbackFromChunks;
        }

        throw new IllegalStateException(
                "docling response has no usable content (status=%s, md/html/text/doctags all null, chunks=%d)"
                        .formatted(status,
                                response.getChunks() != null ? response.getChunks().size() : 0));
    }

    // === ChunkMetadata Mapping ===

    /**
     * 将 Docling 的 Chunk 响应映射为 {@link ChunkMetadata}。
     *
     * <p>使用 {@link ChunkMetadata#of} 工厂方法安全归一化外部输入：
     * headings 为 null 时归一化为空 list，过滤 null 和空白字符串元素；
     * pageNumber 取 {@code pageNumbers} 列表第一个元素（若为空则 0）；
     * contentType 本阶段默认 {@link ChunkContentType#PARAGRAPH}。
     *
     * @param doclingChunk Docling 返回的单个 chunk
     * @return 映射后的 ChunkMetadata
     */
    ChunkMetadata mapChunkMetadata(Chunk doclingChunk) {
        int pageNumber = 0;
        List<Integer> pageNumbers = doclingChunk.getPageNumbers();
        if (pageNumbers != null && !pageNumbers.isEmpty()) {
            pageNumber = pageNumbers.getFirst();
        }

        // contentType 映射：本阶段默认 PARAGRAPH，后续可从 doclingChunk.getDocItems() 细化
        ChunkContentType contentType = ChunkContentType.PARAGRAPH;

        return ChunkMetadata.of(doclingChunk.getHeadings(), pageNumber, contentType);
    }

    // === Processing Metadata ===

    /**
     * 构建 processingMetadata JSON。
     *
     * <p>三层结构：schema_version → stable → conditional → best_effort。
     * 从 filename 和 Docling 响应中自行提取元数据。
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

    // === Validation ===

    /**
     * 校验清洗后的 Markdown 内容有效性。
     *
     * @throws IllegalStateException 校验失败时
     */
    private void validateCleanedMarkdown(String cleanedMarkdown) {
        if (cleanedMarkdown.isBlank()) {
            throw new IllegalStateException("parsed text is empty");
        }
        if (cleanedMarkdown.length() > maxTextLength) {
            throw new IllegalStateException("parsed text exceeds max length");
        }
    }

    // === Utility ===

    /**
     * 格式化 Docling 文档错误列表，用于异常消息中的诊断信息。
     *
     * @param document Docling 响应文档
     * @return 格式化的错误描述（含 ", errors=[...]" 后缀），无错误时返回空字符串
     */
    private static String formatDocumentErrors(Document document) {
        if (document.getErrors() == null || document.getErrors().isEmpty()) {
            return "";
        }
        String details = document.getErrors().stream()
                .map(e -> e.getComponentType() + ": " + e.getErrorMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        return ", errors=[" + details + "]";
    }

    /**
     * 从响应的 chunks 列表中提取文本，作为最后一层降级。
     *
     * <p>当 Docling Serve 的所有内容格式（md/html/text/doctags）都为空时，
     * 尝试从 HybridChunker 产出的 chunk 列表中拼接可用文本。
     * 这是 HybridChunker 的主输出，即使服务器端 exporter 未配置也可能有内容。
     *
     * @param response Docling chunk 响应
     * @return 拼接后的文本，无可用 chunk 时返回 null
     */
    private static String extractTextFromChunks(ChunkDocumentResponse response) {
        if (response.getChunks() == null || response.getChunks().isEmpty()) {
            return null;
        }
        String text = response.getChunks().stream()
                .map(Chunk::getText)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n\n"));
        return text.isBlank() ? null : text;
    }

    private static List<String> extractTitleOutlineSample(String markdown) {
        Matcher matcher = MARKDOWN_HEADING.matcher(markdown);
        List<String> headings = new ArrayList<>();
        while (matcher.find() && headings.size() < MAX_TITLE_OUTLINE_SIZE) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }
}
