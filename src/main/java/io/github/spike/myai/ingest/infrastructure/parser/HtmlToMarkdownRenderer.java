package io.github.spike.myai.ingest.infrastructure.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * cleaned.html -> cleaned.md 渲染 Module。
 */
final class HtmlToMarkdownRenderer {

    private static final FlexmarkHtmlConverter HTML_TO_MARKDOWN =
            FlexmarkHtmlConverter.builder(options()).build();

    String render(String cleanedHtml) {
        return HTML_TO_MARKDOWN.convert(cleanedHtml);
    }

    private static MutableDataSet options() {
        MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-');
        return options;
    }
}
