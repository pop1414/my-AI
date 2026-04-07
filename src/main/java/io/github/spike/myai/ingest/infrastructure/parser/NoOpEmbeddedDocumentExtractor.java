package io.github.spike.myai.ingest.infrastructure.parser;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * 禁用嵌入资源提取的 Extractor。
 *
 * <p>用于抑制 Word/PDF 中嵌入图片、附件等资源对正文文本的污染。
 */
public class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        // 返回 false，明确告诉 Tika 跳过所有嵌入资源。
        // 例如PDF里面的图片，Word里面的附件
        return false;
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
            throws SAXException, IOException {
        // 空实现：因为 shouldParseEmbedded 已返回 false，正常情况下不会被调用。
    }
}

