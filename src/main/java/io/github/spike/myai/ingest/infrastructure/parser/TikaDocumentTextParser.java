package io.github.spike.myai.ingest.infrastructure.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
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

    /** 文本清洗服务，负责 HTML 语义清洗和 Markdown 转换 */
    private final TextCleaningService textCleaningService;
    /** 解析文本最大长度阈值，超过将抛出异常防止 OOM */
    private final int maxTextLength;
    private final DocumentParserRouter router = new DocumentParserRouter();
    private final NativeTextDecoder nativeTextDecoder = new NativeTextDecoder();
    private final TikaParseContextFactory parseContextFactory;
    private final ProcessingMetadataBuilder processingMetadataBuilder;

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
        this.maxTextLength = ingestProperties.getParser().getMaxTextLength();
        this.parseContextFactory = new TikaParseContextFactory(ingestProperties.getParser().isParseEmbeddedResource());
        this.processingMetadataBuilder = new ProcessingMetadataBuilder(objectMapper);
    }

    /**
     * 执行文档解析，产出结构化中间产物。
     *
     * <p>解析链路分为两条路径：
     * <ol>
     *   <li><b>原生 Markdown 路径</b>：若文件扩展名为 md/markdown/mdown/mkd，
     *       跳过 Tika 直接走 {@link #parseNativeMarkdown}；</li>
     *   <li><b>Tika 通用路径</b>：通过 Apache Tika 自动检测文档类型，
     *       产出 XHTML → 委托清洗 → 转为 Markdown，最终组装
     *       {@link DocumentParseResult}。</li>
     * </ol>
     *
     * <p>异常策略：Tika 解析异常和 IO 异常均包装为
     * {@link IllegalStateException}，由上游统一处理。
     *
     * @param filename 原始文件名，用于 MIME 类型推断和元数据
     * @param content  文件原始字节数组
     * @return 结构化解析结果，包含 rawXhtml、cleanedHtml、cleanedMarkdown 和 processingMetadata
     * @throws IllegalStateException 内容为空或解析失败时抛出
     */
    @Override
    public DocumentParseResult parse(String filename, byte[] content) {
        // 输入校验：内容为空时直接拒绝，避免 Tika 产生无意义输出
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }
        DocumentParseRoute route = router.route(filename);
        try {
            return switch (route) {
                case NATIVE_MARKDOWN -> parseNativeMarkdown(filename, content);
                case NATIVE_HTML -> parseNativeHtml(filename, content);
                case TIKA -> parseWithTika(filename, content);
            };
        } catch (CharacterCodingException ignored) {
            // 原生文本严格解码失败时，回退 Tika 让其执行字符集检测。
            return parseWithTika(filename, content);
        }
    }

    /**
     * Tika 通用解析路径。
     *
     * <p>处理非 Markdown 文件（PDF、Word、Excel 等）的完整解析链路：
     * <ol>
     *   <li>通过 {@link AutoDetectParser} 自动识别文档类型；</li>
     *   <li>产出原始 XHTML 并委托 {@link TextCleaningService} 执行
     *       语义清洗（cleanHtml → toMarkdown）；</li>
     *   <li>校验清洗结果的有效性；</li>
     *   <li>组装 {@link DocumentParseResult} 并提取 processingMetadata。</li>
     * </ol>
     *
     * <p>异常策略：Tika 解析异常（{@link TikaException}、{@link SAXException}）
     * 和其他运行时异常分别包装为带不同消息的 {@link IllegalStateException}，
     * 便于排障时区分错误来源。
     *
     * @param filename 原始文件名，用于 MIME 类型推断
     * @param content  文件原始字节数组
     * @return 结构化解析结果
     * @throws IllegalStateException 解析失败时抛出
     */
    private DocumentParseResult parseWithTika(String filename, byte[] content) {
        // 使用 try-with-resources 确保输入流正确关闭
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            // 创建 Tika 自动检测解析器，根据文件内容自动识别文档类型
            AutoDetectParser parser = new AutoDetectParser();
            // 设置 Tika 元数据对象，传入文件名以辅助类型识别
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            // 构建解析上下文，配置 PDF 解析参数和嵌入资源提取策略
            ParseContext context = parseContextFactory.create(parser);
            // 使用 ToXMLContentHandler 将解析结果输出为 XHTML 格式
            ToXMLContentHandler handler = new ToXMLContentHandler();

            // 执行 Tika 核心解析，产出原始 XHTML
            parser.parse(inputStream, handler, metadata, context);

            // 获取原始 XHTML 并依次执行语义清洗和 Markdown 转换
            String rawXhtml = handler.toString();
            String cleanedHtml = textCleaningService.cleanHtml(rawXhtml);
            String cleanedMarkdown = textCleaningService.toMarkdown(cleanedHtml);
            validateCleanedMarkdown(cleanedMarkdown);
            // 组装最终解析结果，包含四类中间产物和 processingMetadata
            return new DocumentParseResult(
                    rawXhtml,
                    cleanedHtml,
                    cleanedMarkdown,
                    processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
        } catch (TikaException | SAXException ex) {
            // Tika 解析异常单独 catch 以提供更精确的错误信息
            throw new IllegalStateException("failed to parse content with tika", ex);
        } catch (Exception ex) {
            // 兜底异常处理，捕获其他运行时异常（如 IOException）
            throw new IllegalStateException("failed to parse content", ex);
        }
    }

    /**
     * 原生 Markdown 文件解析路径。
     *
     * <p>当文件扩展名为 md/markdown/mdown/mkd 时，跳过 Tika 解析，
     * 直接读取 UTF-8 文本并委托 {@link TextCleaningService#cleanNativeMarkdown}
     * 执行最小破坏清洗。此路径避免了 Tika 对纯文本 Markdown 的过度转换
     * （如将代码块格式打乱），保留了原始 Markdown 的结构语义。
     *
     * @param filename 原始文件名
     * @param content  文件原始字节数组
     * @return 结构化解析结果（rawXhtml 和 cleanedHtml 均为空字符串）
     */
    private DocumentParseResult parseNativeMarkdown(String filename, byte[] content) throws CharacterCodingException {
        // 第 1 步：按 BOM 或严格 UTF-8 解码为原始 Markdown 文本
        NativeTextDecoder.DecodedText decodedMarkdown = nativeTextDecoder.decode(content);
        String rawMarkdown = decodedMarkdown.text();
        // 第 2 步：委托 TextCleaningService 执行最小破坏清洗（保留代码块/缩进/表格等结构）
        String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(rawMarkdown);
        // 第 3 步：校验清洗结果的有效性（非空 + 长度阈值）
        validateCleanedMarkdown(cleanedMarkdown);

        // 第 4 步：构造最小 Tika 元数据（仅包含文件名和 MIME 类型）
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown; charset=" + decodedMarkdown.charset().name());

        // 第 5 步：组装解析结果——rawXhtml 和 cleanedHtml 均为空，因为未经过 Tika 管道
        return new DocumentParseResult(
                "",
                "",
                cleanedMarkdown,
                processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
    }

    private DocumentParseResult parseNativeHtml(String filename, byte[] content) throws CharacterCodingException {
        NativeTextDecoder.DecodedText decodedHtml = nativeTextDecoder.decode(content);
        String cleanedHtml = textCleaningService.cleanHtml(decodedHtml.text());
        String cleanedMarkdown = textCleaningService.toMarkdown(cleanedHtml);
        validateCleanedMarkdown(cleanedMarkdown);

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=" + decodedHtml.charset().name());

        return new DocumentParseResult(
                decodedHtml.text(),
                cleanedHtml,
                cleanedMarkdown,
                processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
    }

    /**
     * 校验清洗后的 Markdown 内容的有效性。
     *
     * <p>执行两项校验：
     * <ol>
     *   <li><b>非空校验</b>：cleanedMarkdown 不能为空白，防止空白文档
     *       进入下游的分块和向量化流程；</li>
     *   <li><b>长度校验</b>：不能超过配置的 {@code maxTextLength} 阈值，
     *       防止超大文档导致下游 OOM。</li>
     * </ol>
     *
     * @param cleanedMarkdown 清洗后的 Markdown 内容
     * @throws IllegalStateException 校验失败时抛出
     */
    private void validateCleanedMarkdown(String cleanedMarkdown) {
        // 结果校验：cleanedMarkdown 不能为空
        if (cleanedMarkdown.isBlank()) {
            throw new IllegalStateException("parsed text is empty");
        }
        // 长度校验：防止超长文档导致下游 OOM
        if (cleanedMarkdown.length() > maxTextLength) {
            throw new IllegalStateException("parsed text exceeds max length");
        }
    }

}
