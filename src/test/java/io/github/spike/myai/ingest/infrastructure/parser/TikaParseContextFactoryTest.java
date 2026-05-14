package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TikaParseContextFactoryTest {

    @Test
    @DisplayName("关闭嵌入资源解析时应注入 NoOp extractor")
    void create_shouldDisableEmbeddedExtractionWhenConfigured() {
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new TikaParseContextFactory(false).create(parser);

        assertSame(parser, context.get(Parser.class));
        assertNotNull(context.get(PDFParserConfig.class));
        assertTrue(context.get(EmbeddedDocumentExtractor.class) instanceof NoOpEmbeddedDocumentExtractor);
    }

    @Test
    @DisplayName("开启嵌入资源解析时不应覆盖 extractor")
    void create_shouldNotSetNoOpExtractorWhenEmbeddedParsingEnabled() {
        ParseContext context = new TikaParseContextFactory(true).create(new AutoDetectParser());

        assertNull(context.get(EmbeddedDocumentExtractor.class));
    }
}
