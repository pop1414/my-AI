package io.github.spike.myai.ingest.infrastructure.parser;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;

/**
 * 构建 Tika 解析上下文。
 */
final class TikaParseContextFactory {

    private final boolean parseEmbeddedResource;

    TikaParseContextFactory(boolean parseEmbeddedResource) {
        this.parseEmbeddedResource = parseEmbeddedResource;
    }

    ParseContext create(AutoDetectParser parser) {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        pdfParserConfig.setExtractInlineImages(false);
        context.set(PDFParserConfig.class, pdfParserConfig);

        if (!parseEmbeddedResource) {
            context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());
        }
        return context;
    }
}
