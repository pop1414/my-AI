package io.github.spike.myai.ingest.infrastructure.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

/**
 * 文本清洗服务。
 *
 * <p>职责：
 * <ul>
 *     <li>对 Tika 输出的 XHTML 做语义清洗，产出稳定的 cleaned.html。</li>
 *     <li>将 cleaned.html 转换为标准 Markdown，形成一期主链产物。</li>
 *     <li>对 Markdown 再做轻量规整，输出更稳定的 chunk 输入。</li>
 * </ul>
 */
@Component
public class TextCleaningService {

    private static final Pattern FENCED_CODE_DELIMITER = Pattern.compile("^\\s{0,3}(```+|~~~+).*$");

    /**
     * 控制字符匹配模式，保留换行符（\n）和制表符（\t），
     * 去除其他不可见控制字符以避免干扰后续文本处理
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    /**
     * 独立成行的图片文件名匹配模式（如 image1.png），
     * 这些行是 Tika 从文档中提取图片时生成的占位行，无实际语义价值
     */
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?im)^\\s*image\\d+\\.(png|jpg|jpeg|gif|bmp|webp)\\s*$");
    /**
     * 图片 URL 匹配模式，用于去除文档中残留的远程图片链接
     */
    private static final Pattern IMAGE_URL =
            Pattern.compile("(?im)https?://\\S+\\.(png|jpg|jpeg|gif|bmp|webp)(\\?\\S*)?");
    /**
     * 本地文件 URL 匹配模式（file:// 协议），用于去除文档中嵌入的本地文件引用
     */
    private static final Pattern FILE_URL = Pattern.compile("(?im)file:///\\S+");
    /**
     * 分隔线匹配模式，匹配由连续横线、下划线或等号组成的装饰性分隔线，
     * 这些线条在 Markdown 中可能被误解析为标题或分割标记
     */
    private static final Pattern SEPARATOR_LINE = Pattern.compile("(?m)^\\s*[-_=]{3,}\\s*$");
    /**
     * 多空格/制表符合并模式，将连续空白字符规范化合并为单个空格
     */
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    /**
     * 需要直接移除的 HTML 标签集合。
     * 这些标签承载样式、脚本、元数据、嵌入内容等非语义信息，
     * 在文本清洗阶段应当全部丢弃
     */
    private static final String[] DROP_TAGS = {
            "script", "style", "noscript", "link", "meta", "iframe", "object", "embed", "applet"
    };

    /**
     * 清洗原始 XHTML，输出语义更稳定的 HTML。
     *
     * @param rawXhtml Tika 输出的 XHTML
     * @return cleaned.html 内容
     */
    public String cleanHtml(String rawXhtml) {
        // 空值守卫：上游可能传入 null 或空字符串
        if (rawXhtml == null || rawXhtml.isBlank()) {
            return "";
        }
        // 使用 XML 解析器模式解析 Tika 输出的 XHTML，保持标签结构完整性
        Document document = Jsoup.parse(rawXhtml, "", Parser.xmlParser());
        // 第 1 步：递归移除所有 HTML 注释节点
        removeComments(document);
        // 第 2 步：批量移除无语义价值的标签（脚本、样式、元数据等）
        document.select(String.join(", ", DROP_TAGS)).remove();
        // 第 3 步：标准化标题标签，将 Word 导出的类样式映射为 h1~h3
        standardizeHeadings(document);
        // 第 4 步：将 <img> 替换为纯文本占位符（如 [图片: xxx]），保留语义提示
        replaceImagesWithPlaceholders(document);
        // 第 5 步：剥离所有 style/class/id 等展示性属性，只保留结构
        stripPresentationalAttributes(document);

        // 获取 body 元素作为最终输出的根节点
        Element body = document.body();
        if (body == null) {
            body = document.appendElement("body");
        }
        // 移除导航栏和页脚等噪声区域
        body.select("nav, footer").remove();
        // 移除所有空块级元素（无文本且无子元素的段落/div/span）
        removeEmptyBlocks(body);
        // 返回清洗后的 HTML 片段（仅 body 内部内容）
        return body.html().trim();
    }

    /**
     * 将 cleaned.html 转换为 Markdown。
     *
     * @param cleanedHtml 语义清洗后的 HTML
     * @return cleaned.md 内容
     */
    public String toMarkdown(String cleanedHtml) {
        if (cleanedHtml == null || cleanedHtml.isBlank()) {
            return "";
        }
        // 使用 flexmark-java 的 HTML-to-Markdown 转换器，将清洗后的 HTML 转为标准 Markdown
        String markdown = FlexmarkHtmlConverter.builder().build().convert(cleanedHtml);
        // 对生成的 Markdown 再执行一次轻量规整，去除残留噪声
        return cleanText(markdown);
    }

    /**
     * 对 Markdown/纯文本执行轻量规整。
     *
     * @param rawText 原始文本
     * @return 规整后的文本
     */
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        // 第 1 步：统一换行符为 Unix 风格（\n）
        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");
        // 第 2 步：去除除换行/制表外的所有控制字符
        text = CONTROL_CHARS.matcher(text).replaceAll("");

        String[] lines = text.split("\n", -1);
        StringBuilder cleaned = new StringBuilder(text.length());
        boolean inFencedCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isFencedCodeDelimiter(line)) {
                inFencedCodeBlock = !inFencedCodeBlock;
                cleaned.append(line);
            } else if (inFencedCodeBlock || isIndentedCodeLine(line)) {
                // 代码块仅做控制字符与换行归一化，不压缩内部空格和缩进。
                cleaned.append(line);
            } else {
                cleaned.append(cleanRegularLine(line));
            }
            if (i < lines.length - 1) {
                cleaned.append('\n');
            }
        }

        // 第 3 步：将三个及以上的连续空行压缩为两个空行，保持 Markdown 可读性
        return cleaned.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String cleanRegularLine(String line) {
        String cleaned = IMAGE_FILENAME_LINE.matcher(line).replaceAll("");
        cleaned = IMAGE_URL.matcher(cleaned).replaceAll("");
        cleaned = FILE_URL.matcher(cleaned).replaceAll("");
        cleaned = SEPARATOR_LINE.matcher(cleaned).replaceAll("");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        return stripTrailingWhitespace(cleaned);
    }

    private static boolean isFencedCodeDelimiter(String line) {
        return FENCED_CODE_DELIMITER.matcher(line).matches();
    }

    private static boolean isIndentedCodeLine(String line) {
        return line.startsWith("    ") || line.startsWith("\t");
    }

    private static String stripTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0) {
            char current = line.charAt(end - 1);
            if (current != ' ' && current != '\t') {
                break;
            }
            end--;
        }
        return line.substring(0, end);
    }

    /**
     * 递归移除 HTML 注释节点。
     *
     * <p>HTML 注释中常包含 Word 导出的冗余信息和编辑器元数据，
     * 对后续文本理解和分块无任何价值，需要彻底清除。
     *
     * @param node 待递归扫描的 DOM 节点
     */
    private static void removeComments(Node node) {
        // 复制子节点列表以避免在遍历过程中修改集合导致并发异常
        List<Node> children = new ArrayList<>(node.childNodes());
        for (Node child : children) {
            if (child instanceof Comment) {
                child.remove();
                continue;
            }
            // 递归进入子节点继续清理
            removeComments(child);
        }
    }

    /**
     * 标准化标题标签，将 Word 导出样式类映射为标准 HTML 标题。
     *
     * <p>Tika 解析 .doc/.docx 文件时，生成的是 {@code <p class="MsoTitle">} 而非 {@code <h1>}。
     * 此方法通过 className 识别 Word 样式，将其转换为标准 h1~h3 标签，
     * 确保后续 Markdown 转换能正确生成 # 标题。
     *
     * @param document 待处理的 Jsoup Document 对象
     */
    private static void standardizeHeadings(Document document) {
        // 遍历所有 <p> 标签，检查其 class 属性是否包含 Word 标题样式
        document.select("p").forEach(paragraph -> {
            String className = paragraph.className();
            // MsoTitle 和 MsoHeading1 均视为一级标题
            if (className.contains("MsoTitle") || className.contains("MsoHeading1")) {
                paragraph.tagName("h1");
            } else if (className.contains("MsoHeading2")) {
                paragraph.tagName("h2");
            } else if (className.contains("MsoHeading3")) {
                paragraph.tagName("h3");
            }
        });
    }

    /**
     * 将 {@code <img>} 标签替换为纯文本占位符。
     *
     * <p>图片在 RAG 检索场景中无法被向量化，但其存在本身是重要的语义标记。
     * 如果 img 有 alt 属性，则保留为 {@code [图片: 描述文字]}，
     * 否则仅保留 {@code [图片]}，便于后续分块时保留上下文线索。
     *
     * @param document 待处理的 Jsoup Document 对象
     */
    private static void replaceImagesWithPlaceholders(Document document) {
        document.select("img").forEach(image -> {
            // 提取图片的 alt 文本作为描述信息
            String alt = image.attr("alt").trim();
            // 生成占位文本：有 alt 则附带描述，无则仅标记 [图片]
            String placeholder = alt.isBlank() ? "[图片]" : "[图片: " + alt + "]";
            // 用文本节点直接替换 img 元素
            image.replaceWith(new TextNode(placeholder));
        });
    }

    /**
     * 剥离所有展示性属性（style/class/id）。
     *
     * <p>Word 导出的 HTML 中每个元素都携带大量样式属性（如 margin、font-size 等），
     * 这些属性在 Markdown 转换时会产生噪音或引起格式异常。
     * 剥离后仅保留纯结构，由标题标签和段落标签承载语义层级。
     *
     * @param document 待处理的 Jsoup Document 对象
     */
    private static void stripPresentationalAttributes(Document document) {
        document.getAllElements().forEach(element -> {
            element.removeAttr("style");
            element.removeAttr("class");
            element.removeAttr("id");
        });
    }

    /**
     * 递归移除空块级元素。
     *
     * <p>清洗过程中可能产生内容为空的 &lt;p&gt;、&lt;div&gt;、&lt;span&gt; 标签，
     * 这些空元素在 Markdown 转换后会残留多余空行，影响分块质量。
     * 需同时满足「文本为空」且「无子元素」才移除，
     * 避免误删仅含子元素的结构性标签。
     *
     * @param root 待扫描的根元素（通常是 body）
     */
    private static void removeEmptyBlocks(Element root) {
        root.select("p, div, span").forEach(element -> {
            // 仅当元素内既无文本也无子元素时才移除
            if (element.text().isBlank() && element.children().isEmpty()) {
                element.remove();
            }
        });
    }
}
