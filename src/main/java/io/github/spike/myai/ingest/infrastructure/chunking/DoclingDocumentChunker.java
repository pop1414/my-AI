package io.github.spike.myai.ingest.infrastructure.chunking;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.chunk.request.HybridChunkDocumentRequest;
import ai.docling.serve.api.chunk.request.options.HybridChunkerOptions;
import ai.docling.serve.api.chunk.response.Chunk;
import ai.docling.serve.api.chunk.response.ChunkDocumentResponse;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 基于 Docling Serve HybridChunker 的文档分块实现。
 *
 * <p>通过 {@link DoclingServeApi#chunkSourceWithHybridChunker} 调用 Docling Serve
 * 的 server-side 分块能力（论文验证 faithfulness 97.59），将已清洗的 Markdown 文本
 * 拆分为带结构化元数据（{@link ChunkMetadata}）的 {@link DocumentChunk} 列表。
 *
 * <p>与 {@link io.github.spike.myai.ingest.infrastructure.parser.DoclingDocumentParser}
 * 的关键差异：本类只负责分块（使用 chunks 输出），不负责转换（不使用 document content）。
 * 输入已经是 {@code ProcessDocumentApplicationService} 传入的 cleanedMarkdown，无需再次清洗。
 *
 * <p>DoclingServeApi 的类型不暴露到本类以外，遵循六边形架构 adapter 封装模式。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class DoclingDocumentChunker implements DocumentChunker {

    // === Constants ===

    private static final Logger log = LoggerFactory.getLogger(DoclingDocumentChunker.class);

    // === Instance Fields ===

    /** Docling Serve API 客户端（Arconia 自动配置注入） */
    private final DoclingServeApi doclingServeApi;
    /** HybridChunker 单块最大 token 数 */
    private final int maxTokens;
    /** 是否合并过小块 */
    private final boolean mergePeers;
    /** 分块数量分布摘要（标签 format=markdown） */
    private final DistributionSummary chunkCountSummary;

    // === Constructors ===

    /**
     * 构造器注入：装配 Docling API 客户端、分块配置参数和指标注册中心。
     *
     * @param doclingServeApi  Docling Serve API 客户端（Arconia 自动配置注入）
     * @param ingestProperties ingest 管道配置属性（读取 chunk.maxTokens / chunk.mergePeers）
     * @param meterRegistry Micrometer 指标注册中心
     */
    @Autowired
    public DoclingDocumentChunker(
            DoclingServeApi doclingServeApi,
            IngestProperties ingestProperties,
            MeterRegistry meterRegistry) {
        this.doclingServeApi = doclingServeApi;
        this.maxTokens = ingestProperties.getChunk().getMaxTokens();
        this.mergePeers = ingestProperties.getChunk().isMergePeers();
        this.chunkCountSummary = DistributionSummary.builder("docling.chunk.count")
                .tag("format", "markdown")
                .description("Number of chunks produced by Docling HybridChunker")
                .register(meterRegistry);
    }

    // === Public API ===

    /**
     * 将已清洗的 Markdown 文本通过 Docling Serve HybridChunker 分块。
     *
     * <p>解析链路：
     * <ol>
     *   <li>Base64 编码输入 Markdown；</li>
     *   <li>构造 {@link HybridChunkDocumentRequest}（仅请求 MARKDOWN 格式转换 + 分块）；</li>
     *   <li>调用 {@link DoclingServeApi#chunkSourceWithHybridChunker}；</li>
     *   <li>从 {@link ChunkDocumentResponse#chunks} 提取分块结果；</li>
     *   <li>映射为 {@link DocumentChunk} 列表（text → content, headings/pageNumbers → chunkMetadata）。</li>
     * </ol>
     *
     * <p>异常策略（与 {@code DoclingDocumentParser} 对齐）：
     * <ul>
     *   <li>Docling 4xx（除 408/429）→ {@link DoclingPermanentException}（永久失败，不重试）</li>
     *   <li>Docling 408/429 → {@link DoclingTransientException}（瞬时错误，可重试）</li>
     *   <li>Docling 5xx / 超时 / 网络 → {@link DoclingTransientException}（瞬时错误，可重试）</li>
     *   <li>其他未知异常 → {@link DoclingTransientException}（保守策略：先尝试重试）</li>
     * </ul>
     *
     * @param text 已清洗的 Markdown 文本
     * @return 分块结果列表（含 chunkMetadata 结构化元数据）
     * @throws DoclingPermanentException Docling 4xx 客户端错误（除 408/429）时
     * @throws DoclingTransientException Docling 5xx / 超时 / 网络 / 未知错误时
     */
    @Override
    public List<DocumentChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            ChunkDocumentResponse response = callDoclingChunkApi(text);
            List<DocumentChunk> chunks = mapToDocumentChunks(response);
            chunkCountSummary.record(chunks.size());
            return chunks;
        } catch (HttpClientErrorException ex) {
            int statusCode = ex.getStatusCode().value();
            // 408/429 是瞬时性客户端错误，应按瞬时处理（可重试）
            if (statusCode == 408 || statusCode == 429) {
                throw new DoclingTransientException(
                        "docling chunk returned transient client error: " + ex.getStatusCode(), ex);
            }
            // 其余 4xx → 永久失败（不重试）
            throw new DoclingPermanentException(
                    "docling chunk returned client error: " + ex.getStatusCode(),
                    statusCode,
                    ex);
        } catch (HttpServerErrorException ex) {
            // 5xx 服务端错误 → 瞬时错误（可重试）
            throw new DoclingTransientException(
                    "docling chunk returned server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            // 连接超时 / 读超时 → 瞬时错误（可重试）
            throw new DoclingTransientException("docling chunk connection failed", ex);
        } catch (DoclingPermanentException | DoclingTransientException ex) {
            // 已识别的 Docling 异常直接重新抛出
            throw ex;
        } catch (IllegalStateException ex) {
            // 输入/响应校验错误（空 chunks）— 逻辑错误，不可重试
            throw ex;
        } catch (Exception ex) {
            // 其他未知异常 → 保守策略：视为瞬时错误（先尝试重试）
            throw new DoclingTransientException("unexpected docling chunk error", ex);
        }
    }

    // === Docling API Call ===

    /**
     * 构造请求并调用 Docling Serve 的 HybridChunker 分块接口。
     *
     * @param markdown 已清洗的 Markdown 文本
     * @return Docling 分块响应
     */
    private ChunkDocumentResponse callDoclingChunkApi(String markdown) {
        String base64Content = Base64.getEncoder().encodeToString(markdown.getBytes(StandardCharsets.UTF_8));

        FileSource source = FileSource.builder()
                .base64String(base64Content)
                .filename("input.md")
                .build();

        ConvertDocumentOptions convertOptions = ConvertDocumentOptions.builder()
                .toFormats(List.of(OutputFormat.MARKDOWN))
                .build();

        HybridChunkerOptions chunkerOptions = HybridChunkerOptions.builder()
                .maxTokens(maxTokens)
                .mergePeers(mergePeers)
                .build();

        HybridChunkDocumentRequest request = HybridChunkDocumentRequest.builder()
                .source(source)
                .options(convertOptions)
                .includeConvertedDoc(false)
                .chunkingOptions(chunkerOptions)
                .build();

        log.debug("调用 Docling Serve HybridChunker 分块, markdownLength={}", markdown.length());
        return doclingServeApi.chunkSourceWithHybridChunker(request);
    }

    // === Response Mapping ===

    /**
     * 将 Docling 分块响应映射为 {@link DocumentChunk} 列表。
     *
     * <p>从 {@link ChunkDocumentResponse#chunks} 提取每个 chunk，
     * 映射 {@code Chunk.text()} → {@code DocumentChunk.content()}，
     * {@code Chunk.headings/pageNumbers} → {@link ChunkMetadata}。
     *
     * @param response Docling 分块响应
     * @return 映射后的分块列表
     * @throws IllegalStateException 当响应中无可用 chunks 时
     */
    private List<DocumentChunk> mapToDocumentChunks(ChunkDocumentResponse response) {
        if (response.getChunks() == null || response.getChunks().isEmpty()) {
            throw new IllegalStateException("docling chunk response contains no chunks");
        }

        return response.getChunks().stream()
                .map(this::mapSingleChunk)
                .collect(Collectors.toList());
    }

    /**
     * 将单个 Docling Chunk 映射为 {@link DocumentChunk}。
     *
     * @param doclingChunk Docling 返回的单个 chunk
     * @return 映射后的 DocumentChunk
     */
    private DocumentChunk mapSingleChunk(Chunk doclingChunk) {
        String text = doclingChunk.getText();
        ChunkMetadata metadata = mapChunkMetadata(doclingChunk);
        return new DocumentChunk(text, metadata);
    }

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
    private ChunkMetadata mapChunkMetadata(Chunk doclingChunk) {
        int pageNumber = 0;
        List<Integer> pageNumbers = doclingChunk.getPageNumbers();
        if (pageNumbers != null && !pageNumbers.isEmpty()) {
            pageNumber = pageNumbers.getFirst();
        }

        // contentType 映射：本阶段默认 PARAGRAPH，后续可从 doclingChunk.getDocItems() 细化
        ChunkContentType contentType = ChunkContentType.PARAGRAPH;

        return ChunkMetadata.of(doclingChunk.getHeadings(), pageNumber, contentType);
    }
}
