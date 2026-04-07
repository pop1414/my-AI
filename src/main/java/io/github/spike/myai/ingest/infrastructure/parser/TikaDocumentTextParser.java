package io.github.spike.myai.ingest.infrastructure.parser;

import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * 基于 Apache Tika 的文档解析实现。
 */
@Component
public class TikaDocumentTextParser implements DocumentTextParser {

    private final TextCleaningService textCleaningService;
    // 最大文本长度（默认200万字符，防止超大文本占满内存）
    private final int maxTextLength;
    // 是否解析嵌入资源（默认false，即禁用）
    private final boolean parseEmbeddedResource;

    public TikaDocumentTextParser(
            TextCleaningService textCleaningService,
            @Value("${myai.ingest.parser.max-text-length:2000000}") int maxTextLength,
            @Value("${myai.ingest.parser.parse-embedded-resource:false}") boolean parseEmbeddedResource) {
        this.textCleaningService = textCleaningService;
        this.maxTextLength = maxTextLength;
        this.parseEmbeddedResource = parseEmbeddedResource;
    }

    @Override
    public String parse(String filename, byte[] content) {
        // 空内容抛出异常
        if (content == null || content.length == 0) {
            throw new IllegalStateException("empty source content");
        }
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            // 步骤1：初始化Tika核心组件
            // 自动识别文档类型（PDF/Word等）
            AutoDetectParser parser = new AutoDetectParser();
            // 设置最大文本长度，防止异常文档输出超大文本导致内存风险。
            BodyContentHandler handler = new BodyContentHandler(maxTextLength);
            Metadata metadata = new Metadata();
            // 设置文件名，帮助Tika识别文档类型
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            // 步骤2：配置解析上下文（核心规则配置）
            ParseContext context = new ParseContext();
            // 注册 parser 本体，支持递归解析时使用统一解析器。
            context.set(Parser.class, parser);

            // PDF 专项：禁用内联图片提取，减少路径噪音。
            PDFParserConfig pdfParserConfig = new PDFParserConfig();
            pdfParserConfig.setExtractInlineImages(false);
            context.set(PDFParserConfig.class, pdfParserConfig);

            // 默认关闭嵌入资源解析，避免 image1.jpeg、临时文件路径污染正文。
            if (!parseEmbeddedResource) {
                context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());
            }

            // 步骤3：执行Tika解析（核心操作）
            parser.parse(inputStream, handler, metadata, context);
            // 步骤4：文本清洗（调用TextCleaningService）
            String cleanedText = textCleaningService.cleanText(handler.toString());

            // 后置校验：清洗后文本为空则抛异常
            if (cleanedText.isBlank()) {
                throw new IllegalStateException("parsed text is empty");
            }
            return cleanedText;
        } catch (TikaException | SAXException ex) {
            throw new IllegalStateException("failed to parse content with tika", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse content", ex);
        }
    }
}

