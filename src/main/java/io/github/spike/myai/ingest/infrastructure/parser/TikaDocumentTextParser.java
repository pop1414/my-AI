package io.github.spike.myai.ingest.infrastructure.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.UnsupportedDocumentFormatException;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * 基于 Apache Tika 的文档解析实现。
 *
 * <p>负责完成从原始文件字节到结构化中间产物（{@link DocumentParseResult}）的完整解析链路：
 * <ol>
 *   <li>调用 Tika 产出原始 XHTML；</li>
 *   <li>委托 {@link TextCleaningService} 语义清洗产出 cleaned HTML；</li>
 *   <li>委托 {@link TextCleaningService} 转换为 cleaned Markdown；</li>
 *   <li>从 Tika 元数据与 Markdown 内容中提取 processingMetadata。</li>
 * </ol>
 *
 * <p>Story 3.1 重构后：DOCLING 路由委托 {@link DoclingDocumentParser}，不再走 Tika 路径。
 * 本类将在 Story 3.2 整体删除。
 *
 * @author Spike
 * @since 1.0.0
 * @deprecated Story 3.2 将整体删除 TikaDocumentTextParser，DOCLING 路由已委托给 {@link DoclingDocumentParser}
 */
@Primary
@Component
@SuppressWarnings("deprecation")
public class TikaDocumentTextParser implements DocumentTextParser {

    /** 文本清洗服务，负责 HTML 语义清洗和 Markdown 转换 */
    private final TextCleaningService textCleaningService;
    /** Docling 解析器，DOCLING 路由委托目标（Story 3.1 新增注入） */
    private final DocumentTextParser doclingDocumentParser;
    /** 解析文本最大长度阈值，超过将抛出异常防止 OOM */
    private final int maxTextLength;
    private final DocumentParserRouter router = new DocumentParserRouter();
    private final NativeTextDecoder nativeTextDecoder = new NativeTextDecoder();
    private final TikaParseContextFactory parseContextFactory;
    private final ProcessingMetadataBuilder processingMetadataBuilder;

    /**
     * 构造器注入：装配解析链路所需的清洗服务、序列化器和配置参数。
     *
     * @param textCleaningService   文本清洗服务
     * @param doclingDocumentParser Docling 解析器（{@code @Qualifier("docling")} 定位）
     * @param objectMapper          JSON 序列化器
     * @param ingestProperties      ingest 管道配置属性
     */
    public TikaDocumentTextParser(
            TextCleaningService textCleaningService,
            @Qualifier("docling") DocumentTextParser doclingDocumentParser,
            ObjectMapper objectMapper,
            IngestProperties ingestProperties) {
        this.textCleaningService = textCleaningService;
        this.doclingDocumentParser = doclingDocumentParser;
        this.maxTextLength = ingestProperties.getParser().getMaxTextLength();
        this.parseContextFactory = new TikaParseContextFactory(ingestProperties.getParser().isParseEmbeddedResource());
        this.processingMetadataBuilder = new ProcessingMetadataBuilder(objectMapper);
    }

    /**
     * 执行文档解析，产出结构化中间产物。
     *
     * <p>Story 3.1 重构后：所有支持格式走 {@link DocumentParseRoute#DOCLING}，
     * 委托 {@link DoclingDocumentParser} 处理。不支持的格式由
     * {@link DocumentParserRouter#route(String)} 抛出 {@link UnsupportedDocumentFormatException}。
     *
     * @param filename 原始文件名，用于 MIME 类型推断和元数据
     * @param content  文件原始字节数组
     * @return 结构化解析结果，包含 cleanedMarkdown 和 processingMetadata
     * @throws IllegalStateException                  内容为空时抛出
     * @throws UnsupportedDocumentFormatException     不支持的格式时抛出
     */
    @Override
    public DocumentParseResult parse(String filename, byte[] content) {
        // 输入校验：内容为空时直接拒绝
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }
        // route() 对不支持格式直接抛出 UnsupportedDocumentFormatException
        DocumentParseRoute route = router.route(filename);
        return switch (route) {
            case DOCLING -> doclingDocumentParser.parse(filename, content);
            // REJECT 分支实际不可达（route() 已抛异常），保留以满足编译器穷举要求
            case REJECT -> throw new UnsupportedDocumentFormatException("unknown");
        };
    }

    /**
     * Tika 通用解析路径。
     *
     * <p>处理非 Markdown 文件（PDF、Word、Excel 等）的完整解析链路。
     *
     * @deprecated Story 3.2 删除 TikaDocumentTextParser 时一并移除
     */
    @Deprecated
    private DocumentParseResult parseWithTika(String filename, byte[] content) {
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            ParseContext context = parseContextFactory.create(parser);
            ToXMLContentHandler handler = new ToXMLContentHandler();

            parser.parse(inputStream, handler, metadata, context);

            String rawXhtml = handler.toString();
            String cleanedHtml = textCleaningService.cleanHtml(rawXhtml);
            String cleanedMarkdown = textCleaningService.toMarkdown(cleanedHtml);
            validateCleanedMarkdown(cleanedMarkdown);
            return new DocumentParseResult(
                    cleanedMarkdown,
                    processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
        } catch (TikaException | SAXException ex) {
            throw new IllegalStateException("failed to parse content with tika", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse content", ex);
        }
    }

    /**
     * 原生 Markdown 文件解析路径。
     *
     * @deprecated Story 3.2 删除 TikaDocumentTextParser 时一并移除
     */
    @Deprecated
    private DocumentParseResult parseNativeMarkdown(String filename, byte[] content) throws CharacterCodingException {
        NativeTextDecoder.DecodedText decodedMarkdown = nativeTextDecoder.decode(content);
        String rawMarkdown = decodedMarkdown.text();
        String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(rawMarkdown);
        validateCleanedMarkdown(cleanedMarkdown);

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown; charset=" + decodedMarkdown.charset().name());

        return new DocumentParseResult(
                cleanedMarkdown,
                processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
    }

    /**
     * 原生 HTML 文件解析路径。
     *
     * @deprecated Story 3.2 删除 TikaDocumentTextParser 时一并移除
     */
    @Deprecated
    private DocumentParseResult parseNativeHtml(String filename, byte[] content) throws CharacterCodingException {
        NativeTextDecoder.DecodedText decodedHtml = nativeTextDecoder.decode(content);
        String cleanedHtml = textCleaningService.cleanHtml(decodedHtml.text());
        String cleanedMarkdown = textCleaningService.toMarkdown(cleanedHtml);
        validateCleanedMarkdown(cleanedMarkdown);

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=" + decodedHtml.charset().name());

        return new DocumentParseResult(
                cleanedMarkdown,
                processingMetadataBuilder.build(filename, metadata, cleanedMarkdown));
    }

    /**
     * 校验清洗后的 Markdown 内容的有效性。
     *
     * @param cleanedMarkdown 清洗后的 Markdown 内容
     * @throws IllegalStateException 校验失败时抛出
     */
    private void validateCleanedMarkdown(String cleanedMarkdown) {
        if (cleanedMarkdown.isBlank()) {
            throw new IllegalStateException("parsed text is empty");
        }
        if (cleanedMarkdown.length() > maxTextLength) {
            throw new IllegalStateException("parsed text exceeds max length");
        }
    }

}
