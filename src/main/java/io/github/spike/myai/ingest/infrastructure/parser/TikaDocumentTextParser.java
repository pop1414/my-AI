package io.github.spike.myai.ingest.infrastructure.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * 基于 Apache Tika 的文档解析实现。
 *
 * <p>负责完成从原始文件字节到结构化中间产物（{@link DocumentParseResult}）的完整解析链路：
 * <ol>
 *   <li>调用 Tika 产出原始 XHTML；</li>
 *   <li>委托 {@link TextCleaningService} 语义清洗产出 cleaned.html；</li>
 *   <li>委托 {@link TextCleaningService} 转换为 cleaned.md；</li>
 *   <li>从 Tika 元数据与 Markdown 内容中提取 processingMetadata。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Component
public class TikaDocumentTextParser implements DocumentTextParser {

    /**
     * Markdown 标题行匹配模式，用于从 cleaned.md 中提取标题大纲样本。
     * 匹配 1~6 级标题（# 至 ######），捕获标题文本内容
     */
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    /**
     * Tika 元数据中可能包含的页码字段键列表。
     * 按优先级排序：XMP 标准键 > 通用 meta 键 > Tika 自定义键
     */
    private static final List<String> PAGE_COUNT_KEYS = List.of("xmpTPg:NPages", "meta:page-count", "Page-Count");

    /** 文本清洗服务，负责 HTML 语义清洗和 Markdown 转换 */
    private final TextCleaningService textCleaningService;
    /** JSON 序列化器，用于序列化 processingMetadata */
    private final ObjectMapper objectMapper;
    /** 解析文本最大长度阈值，超过将抛出异常防止 OOM */
    private final int maxTextLength;
    /** 是否解析嵌入资源（如图片中的文本），默认关闭以节省内存 */
    private final boolean parseEmbeddedResource;

    /**
     * 构造器注入：装配解析链路所需的清洗服务、序列化器和配置参数。
     *
     * @param textCleaningService 文本清洗服务
     * @param objectMapper        JSON 序列化器
     * @param ingestProperties    ingest 管道配置属性
     */
    public TikaDocumentTextParser(
            TextCleaningService textCleaningService,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties) {
        this.textCleaningService = textCleaningService;
        this.objectMapper = objectMapper;
        this.maxTextLength = ingestProperties.getParser().getMaxTextLength();
        this.parseEmbeddedResource = ingestProperties.getParser().isParseEmbeddedResource();
    }

    @Override
    public DocumentParseResult parse(String filename, byte[] content) {
        // 输入校验：内容为空时直接拒绝，避免 Tika 产生无意义输出
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }
        // 使用 try-with-resources 确保输入流正确关闭
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            // 创建 Tika 自动检测解析器，根据文件内容自动识别文档类型
            AutoDetectParser parser = new AutoDetectParser();
            // 设置 Tika 元数据对象，传入文件名以辅助类型识别
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            // 构建解析上下文，配置 PDF 解析参数和嵌入资源提取策略
            ParseContext context = buildParseContext(parser);
            // 使用 ToXMLContentHandler 将解析结果输出为 XHTML 格式
            ToXMLContentHandler handler = new ToXMLContentHandler();

            // 执行 Tika 核心解析，产出原始 XHTML
            parser.parse(inputStream, handler, metadata, context);

            // 获取原始 XHTML 并依次执行语义清洗和 Markdown 转换
            String rawXhtml = handler.toString();
            String cleanedHtml = textCleaningService.cleanHtml(rawXhtml);
            String cleanedMarkdown = textCleaningService.toMarkdown(cleanedHtml);
            // 结果校验：cleanedMarkdown 不能为空
            if (cleanedMarkdown.isBlank()) {
                throw new IllegalStateException("parsed text is empty");
            }
            // 长度校验：防止超长文档导致下游 OOM
            if (cleanedMarkdown.length() > maxTextLength) {
                throw new IllegalStateException("parsed text exceeds max length");
            }
            // 组装最终解析结果，包含四类中间产物和 processingMetadata
            return new DocumentParseResult(
                    rawXhtml,
                    cleanedHtml,
                    cleanedMarkdown,
                    buildProcessingMetadata(filename, metadata, cleanedMarkdown));
        } catch (TikaException | SAXException ex) {
            // Tika 解析异常单独 catch 以提供更精确的错误信息
            throw new IllegalStateException("failed to parse content with tika", ex);
        } catch (Exception ex) {
            // 兜底异常处理，捕获其他运行时异常（如 IOException）
            throw new IllegalStateException("failed to parse content", ex);
        }
    }

    /**
     * 构建 Tika 解析上下文。
     *
     * <p>配置项：
     * <ul>
     *   <li>设置 PDF 解析器不提取内联图片（减少内存开销）；</li>
     *   <li>按配置决定是否忽略嵌入文档（如 Excel 中的嵌入 PDF）。</li>
     * </ul>
     *
     * @param parser Tika 自动检测解析器实例
     * @return 配置完成的解析上下文
     */
    private ParseContext buildParseContext(AutoDetectParser parser) {
        ParseContext context = new ParseContext();
        // 注册主解析器到上下文
        context.set(Parser.class, parser);

        // 配置 PDF 解析参数：禁用内联图片提取以降低内存开销
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setExtractInlineImages(false);
        context.set(PDFParserConfig.class, pdfParserConfig);

        // 当配置关闭嵌入资源解析时，注入空操作提取器跳过所有嵌入文档
        if (!parseEmbeddedResource) {
            context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());
        }
        return context;
    }

    /**
     * 构建文档处理元数据 JSON。
     *
     * <p>元数据结构分为三层：
     * <ul>
     *   <li>{@code stable}：始终可获取的稳定元数据（文件名、扩展名、MIME 类型等）；</li>
     *   <li>{@code conditional}：有条件存在的元数据（语言、页码、标题等）；</li>
     *   <li>{@code best_effort}：预留的最佳尽力元数据（当前为空对象）。</li>
     * </ul>
     *
     * @param filename        原始文件名
     * @param metadata        Tika 解析产出的元数据
     * @param cleanedMarkdown 清洗后的 Markdown 内容
     * @return 序列化后的 processingMetadata JSON 字符串
     */
    private String buildProcessingMetadata(String filename, Metadata metadata, String cleanedMarkdown) {
        // 使用 LinkedHashMap 保持字段插入顺序，确保 JSON 输出稳定可读
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "v1");
        // stable：始终存在的元数据层
        root.put("stable", buildStableMetadata(filename, metadata));

        // conditional：按需存在的元数据层，仅在非空时写入
        Map<String, Object> conditional = buildConditionalMetadata(metadata, cleanedMarkdown);
        if (!conditional.isEmpty()) {
            root.put("conditional", conditional);
        }

        // best_effort：预留扩展层，当前版本为空对象
        root.put("best_effort", Map.of());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize processing metadata", ex);
        }
    }

    /**
     * 构建稳定元数据层。
     *
     * <p>稳定元数据在任何文档类型下都应可获取，包括：
     * 源文件名、文件扩展名、MIME 类型、质量标记和处理时间戳。
     *
     * @param filename 原始文件名
     * @param metadata Tika 解析元数据
     * @return 稳定元数据键值对
     */
    private static Map<String, Object> buildStableMetadata(String filename, Metadata metadata) {
        Map<String, Object> stable = new LinkedHashMap<>();
        stable.put("source_file", filename);
        stable.put("file_ext", fileExtension(filename));
        // MIME 类型：优先使用 Tika 检测值，回退为通用二进制类型
        stable.put("mime_type", firstNonBlank(metadata.get(Metadata.CONTENT_TYPE), "application/octet-stream"));
        // quality 标记当前版本固定为 high
        stable.put("quality", "high");
        // 使用 ISO-8601 格式记录处理时间
        stable.put("created_at", Instant.now().toString());
        return stable;
    }

    /**
     * 构建条件元数据层。
     *
     * <p>条件元数据仅在能获取到时写入，包括语言、页码、标题及标题大纲样本。
     * 如果 Tika 未提供 primary_title，则回退使用 Markdown 中的第一个标题。
     *
     * @param metadata        Tika 解析元数据
     * @param cleanedMarkdown 清洗后的 Markdown 内容
     * @return 条件元数据键值对（可能为空 Map）
     */
    private static Map<String, Object> buildConditionalMetadata(Metadata metadata, String cleanedMarkdown) {
        Map<String, Object> conditional = new LinkedHashMap<>();
        // 语言检测：优先取自定义 language 键，回退取 Content-Language
        String language = firstNonBlank(metadata.get("language"), metadata.get(Metadata.CONTENT_LANGUAGE));
        if (language != null) {
            conditional.put("language", language);
        }

        // 页码提取：从多个可能的 Tika 元数据键中尝试解析
        Integer pageCount = parsePageCount(metadata);
        if (pageCount != null) {
            conditional.put("page_count", pageCount);
        }

        // 标题提取：优先使用 Tika 元数据中的标题
        String title = firstNonBlank(metadata.get(TikaCoreProperties.TITLE));
        if (title != null) {
            conditional.put("primary_title", title);
        }

        // 标题大纲样本：从 Markdown 中提取前 3 个标题作为内容结构预览
        List<String> titleOutlineSample = extractTitleOutlineSample(cleanedMarkdown);
        if (!titleOutlineSample.isEmpty()) {
            // 如果 Tika 没有提供标题，使用 Markdown 的第一个标题作为回退
            if (title == null) {
                conditional.put("primary_title", titleOutlineSample.getFirst());
            }
            conditional.put("title_outline_sample", titleOutlineSample);
        }
        return conditional;
    }

    /**
     * 从 Tika 元数据中解析页码数。
     *
     * <p>按 {@link #PAGE_COUNT_KEYS} 定义的优先级顺序逐一尝试，
     * 返回第一个成功解析的整数值。若所有键均无法解析则返回 null。
     *
     * @param metadata Tika 解析元数据
     * @return 页码数，无法获取时返回 null
     */
    private static Integer parsePageCount(Metadata metadata) {
        for (String key : PAGE_COUNT_KEYS) {
            String raw = metadata.get(key);
            // 跳过空值和空白字符串
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // 当前 key 的值不是有效整数，继续尝试下一个 key
            }
        }
        return null;
    }

    /**
     * 从 Markdown 内容中提取标题大纲样本。
     *
     * <p>提取前 3 个 Markdown 标题行（1~6 级），作为文档结构预览。
     * 用于在元数据中提供文档内容概览，方便快速判断文档主题。
     *
     * @param markdown cleaned.md 内容
     * @return 标题文本列表（最多 3 条）
     */
    private static List<String> extractTitleOutlineSample(String markdown) {
        Matcher matcher = MARKDOWN_HEADING.matcher(markdown);
        List<String> headings = new java.util.ArrayList<>();
        // 最多提取 3 个标题作为大纲样本
        while (matcher.find() && headings.size() < 3) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    /**
     * 从文件名中提取扩展名（不含点号，统一小写）。
     *
     * <p>边界情况处理：
     * <ul>
     *   <li>文件名为空 → 返回 {@code "bin"}；</li>
     *   <li>无扩展名 → 返回 {@code "bin"}；</li>
     *   <li>扩展名为空（以点结尾）→ 返回 {@code "bin"}。</li>
     * </ul>
     *
     * @param filename 原始文件名
     * @return 小写扩展名（不含点号）
     */
    private static String fileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "bin";
        }
        int index = filename.lastIndexOf('.');
        // 无点号或点号在末尾均视为无扩展名
        if (index < 0 || index == filename.length() - 1) {
            return "bin";
        }
        // 截取点号之后的部分并转为小写
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 返回第一个非空字符串。
     *
     * <p>按参数顺序依次检查，返回首个非 null 且非空白（去除首尾空白后）的字符串。
     * 常用于从多个候选元数据键中按优先级取值。
     *
     * @param values 候选字符串数组（按优先级排列）
     * @return 首个非空字符串，全为空时返回 null
     */
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
