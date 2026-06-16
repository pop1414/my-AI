package io.github.spike.myai.ingest.infrastructure.parser;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.source.FileSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 基于 Docling Serve 的文档解析实现。
 *
 * <p>通过 Arconia 自动配置的 {@link DoclingServeApi} 调用 Docling Serve 的
 * HybridChunker 接口，一步完成文档转换和分块。所有 Docling 特定逻辑
 * （Base64 编码、请求构造、响应映射）封装在此 adapter 内，
 * {@link DoclingServeApi} 的类型不暴露到本类以外。
 *
 * <p>当前与 {@link TikaDocumentTextParser} 共存（通过 {@code @Qualifier("docling")} 区分），
 * Story 3.1 路由重构后将决定最终的 Bean 注册策略。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
@Qualifier("docling")
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

    // === Instance Fields ===

    private final DoclingServeApi doclingServeApi;
    private final ObjectMapper objectMapper;
    private final int maxTextLength;
    private final Clock clock;

    // === Constructors ===

    /**
     * 构造器注入：装配 Docling API 客户端、序列化器和配置参数。
     *
     * @param doclingServeApi  Docling Serve API 客户端（Arconia 自动配置注入）
     * @param objectMapper     JSON 序列化器
     * @param ingestProperties ingest 管道配置属性
     */
    public DoclingDocumentParser(
            DoclingServeApi doclingServeApi,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties) {
        this(doclingServeApi, objectMapper, ingestProperties, Clock.systemUTC());
    }

    /** Package-private 构造器，供测试注入 Clock。 */
    DoclingDocumentParser(
            DoclingServeApi doclingServeApi,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties,
            Clock clock) {
        this.doclingServeApi = doclingServeApi;
        this.objectMapper = objectMapper;
        this.maxTextLength = ingestProperties.getParser().getMaxTextLength();
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
     *   <li>从响应映射 cleanedMarkdown 和 processingMetadata。</li>
     * </ol>
     *
     * <p>异常策略：所有异常统一包装为 {@link IllegalStateException}，
     * 由上游 Worker 层统一处理。DoclingParseException 异常层次（4xx/5xx 映射）
     * 是 Story 2.4 的职责。
     *
     * @param filename 原始文件名
     * @param content  文件字节数组
     * @return 解析结果（cleanedMarkdown + processingMetadata）
     * @throws IllegalStateException 内容为空或解析失败时
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
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse content with docling", ex);
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
        String base64Content = Base64.getEncoder().encodeToString(content);

        FileSource source = FileSource.builder()
                .base64String(base64Content)
                .filename(filename)
                .build();

        HybridChunkerOptions chunkerOptions = HybridChunkerOptions.builder()
                .maxTokens(DEFAULT_MAX_TOKENS)
                .mergePeers(DEFAULT_MERGE_PEERS)
                .build();

        HybridChunkDocumentRequest request = HybridChunkDocumentRequest.builder()
                .source(source)
                .includeConvertedDoc(true)
                .chunkingOptions(chunkerOptions)
                .build();

        log.debug("调用 Docling Serve HybridChunker, filename={}, contentLength={}", filename, content.length);
        return doclingServeApi.chunkSourceWithHybridChunker(request);
    }

    // === Response Mapping ===

    /**
     * 将 Docling 响应映射为 {@link DocumentParseResult}。
     */
    private DocumentParseResult mapToDocumentParseResult(String filename, ChunkDocumentResponse response) {
        String cleanedMarkdown = extractCleanedMarkdown(response);
        validateCleanedMarkdown(cleanedMarkdown);

        String processingMetadata = buildProcessingMetadata(filename, cleanedMarkdown);
        return new DocumentParseResult(cleanedMarkdown, processingMetadata);
    }

    /**
     * 从 Docling 响应中提取 cleanedMarkdown。
     *
     * @throws IllegalStateException 当 documents 列表为空或 markdownContent 为 null 时
     */
    private String extractCleanedMarkdown(ChunkDocumentResponse response) {
        if (response.getDocuments() == null || response.getDocuments().isEmpty()) {
            throw new IllegalStateException("docling response contains no documents");
        }

        var document = response.getDocuments().getFirst();
        if (document.getContent() == null || document.getContent().getMarkdownContent() == null) {
            throw new IllegalStateException("docling response document has no markdown content");
        }

        return document.getContent().getMarkdownContent();
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
     * <p>复用 {@link ProcessingMetadataBuilder} 的三层结构：
     * schema_version → stable → conditional → best_effort。
     * Docling 响应不再提供 Tika 的 {@code Metadata} 对象，
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

    private static List<String> extractTitleOutlineSample(String markdown) {
        Matcher matcher = MARKDOWN_HEADING.matcher(markdown);
        List<String> headings = new ArrayList<>();
        while (matcher.find() && headings.size() < MAX_TITLE_OUTLINE_SIZE) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }
}
