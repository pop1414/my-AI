package io.github.spike.myai.ingest.infrastructure.parser;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

/**
 * cleaned.html 语义清洗 Module。
 */
final class HtmlSemanticCleaner {

    private static final String[] DROP_TAGS = {
            "script", "style", "noscript", "link", "meta", "iframe", "object", "embed", "applet"
    };

    String clean(String rawXhtml) {
        if (rawXhtml == null || rawXhtml.isBlank()) {
            return "";
        }

        Document document = Jsoup.parse(rawXhtml, "", Parser.xmlParser());
        removeComments(document);
        document.select(String.join(", ", DROP_TAGS)).remove();
        standardizeHeadings(document);
        replaceImagesWithPlaceholders(document);
        stripPresentationalAttributes(document);

        Element body = document.body();
        if (body == null) {
            body = document.appendElement("body");
        }
        body.select("nav, aside, footer").remove();
        removeEmptyBlocks(body);
        return body.html().trim();
    }

    private static void removeComments(Node node) {
        List<Node> children = new ArrayList<>(node.childNodes());
        for (Node child : children) {
            if (child instanceof Comment) {
                child.remove();
                continue;
            }
            removeComments(child);
        }
    }

    private static void standardizeHeadings(Document document) {
        document.select("p").forEach(paragraph -> {
            String className = paragraph.className();
            if (className.contains("MsoTitle") || className.contains("MsoHeading1")) {
                paragraph.tagName("h1");
            } else if (className.contains("MsoHeading2")) {
                paragraph.tagName("h2");
            } else if (className.contains("MsoHeading3")) {
                paragraph.tagName("h3");
            }
        });
    }

    private static void replaceImagesWithPlaceholders(Document document) {
        document.select("img").forEach(image -> {
            String alt = image.attr("alt").trim();
            String placeholder = alt.isBlank() ? "[图片]" : "[图片: " + alt + "]";
            image.replaceWith(new TextNode(placeholder));
        });
    }

    private static void stripPresentationalAttributes(Document document) {
        document.getAllElements().forEach(element -> {
            element.removeAttr("style");
            element.removeAttr("class");
            element.removeAttr("id");
        });
    }

    private static void removeEmptyBlocks(Element root) {
        root.select("p, div, span").forEach(element -> {
            if (element.text().isBlank() && element.children().isEmpty()) {
                element.remove();
            }
        });
    }
}
