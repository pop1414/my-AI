package io.github.spike.myai.ingest.infrastructure.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
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

    /**
     * 围栏代码块分隔符匹配模式。
     *
     * <p>匹配以 3 个及以上反引号（{@code ```}）或波浪号（{@code ~~~}）
     * 开头的行，用于标记 Markdown 代码块的起止边界。边界行之前允许
     * 最多 3 个前导空格（兼容缩进代码块）。
     */
    private static final Pattern FENCED_CODE_DELIMITER = Pattern.compile("^\\s{0,3}(```+|~~~+).*$");
    /**
     * 危险 HTML 块级元素匹配模式（如 &lt;script&gt;...&lt;/script&gt;）。
     *
     * <p>用于移除 Markdown 中残留的完整危险 HTML 标签块，
     * 采用 DOTALL 模式（{@code (?s)}）以支持跨行匹配。
     */
    private static final Pattern DANGEROUS_HTML_BLOCK =
            Pattern.compile("(?is)<(script|style|iframe|object|embed|applet)\\b[^>]*>.*?</\\1>");
    /**
     * 危险 HTML 单标签匹配模式（开标签或闭标签）。
     *
     * <p>用于移除未被 {@link #DANGEROUS_HTML_BLOCK} 覆盖的孤立危险标签，
     * 如不成对的 {@code <script>} 或 {@code </iframe>}。
     */
    private static final Pattern DANGEROUS_HTML_TAG =
            Pattern.compile("(?is)</?(script|style|iframe|object|embed|applet)\\b[^>]*>");
    /**
     * 危险 HTML 开标签匹配模式，用于识别跨行 raw HTML 块的起点。
     *
     * <p>当检测到危险开标签（如 {@code <script>}）但本行未找到对应闭标签时，
     * 标记进入"危险 HTML 块"状态，后续行将跳过直到找到匹配的闭标签，
     * 实现跨行危险 HTML 块的整体移除。
     */
    private static final Pattern DANGEROUS_HTML_OPEN_TAG =
            Pattern.compile("(?i)<(script|style|iframe|object|embed|applet)\\b[^>]*>");
    /**
     * 不可见格式化字符匹配模式。
     *
     * <p>匹配零宽空格（U+200B）、零宽非连接符（U+200C）、
     * 零宽连接符（U+200D）和 BOM（U+FEFF）等不可见字符，
     * 这些字符常由 Word 或 HTML 编辑器引入，对文本理解无意义。
     */
    private static final Pattern INVISIBLE_FORMATTING_CHARS = Pattern.compile("[\\uFEFF\\u200B\\u200C\\u200D]");

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
     * 独立成行的图片 URL 匹配模式。
     *
     * <p>与 {@link #IMAGE_URL} 的区别在于采用整行匹配（{@code ^...$}），
     * 仅移除图片 URL 独占一行的场景，避免误伤正文中内嵌的图片链接。
     */
    private static final Pattern IMAGE_URL_LINE =
            Pattern.compile("(?im)^\\s*https?://\\S+\\.(png|jpg|jpeg|gif|bmp|webp)(\\?\\S*)?\\s*$");
    /**
     * 本地文件 URL 匹配模式（file:// 协议），用于去除文档中嵌入的本地文件引用
     */
    private static final Pattern FILE_URL = Pattern.compile("(?im)file:///\\S+");
    /**
     * 独立成行的本地文件 URL 匹配模式。
     *
     * <p>采用整行匹配，仅移除 {@code file:///} 协议 URL 独占一行的情况，
     * 避免误删正文中行内出现的本地文件引用路径。
     */
    private static final Pattern FILE_URL_LINE = Pattern.compile("(?im)^\\s*file:///\\S+\\s*$");
    /**
     * 常见页眉页脚噪音行匹配模式。
     *
     * <p>覆盖弱结构 PDF 中常见的“内部评审稿”和“第 N 页 / 质检热线 ...”样式，
     * 只在整行匹配时删除，避免误伤正文中的解释性描述。
     */
    private static final Pattern PAGE_CHROME_NOISE_LINE =
            Pattern.compile("(?im)^\\s*(内部评审稿|第\\s*\\d+\\s*页\\s*(?:/\\s*质检热线\\s*[-0-9]+)?)\\s*$");
    /**
     * 分隔线匹配模式，匹配由连续横线、下划线或等号组成的装饰性分隔线，
     * 这些线条在 Markdown 中可能被误解析为标题或分割标记
     */
    private static final Pattern SEPARATOR_LINE = Pattern.compile("(?m)^\\s*[-_=]{3,}\\s*$");
    /**
     * 多空格/制表符合并模式，将连续空白字符规范化合并为单个空格
     */
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    private static final Pattern WORD_BULLET_LINE = Pattern.compile("^\\s*[·•]\\s+(.+)$");
    private static final Pattern MARKDOWN_TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern MARKDOWN_TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final FlexmarkHtmlConverter HTML_TO_MARKDOWN = FlexmarkHtmlConverter.builder(htmlToMarkdownOptions())
            .build();
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
        // 移除导航栏、侧边栏和页脚等噪声区域
        body.select("nav, aside, footer").remove();
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
        String markdown = HTML_TO_MARKDOWN.convert(cleanedHtml);
        // 对生成的 Markdown 再执行一次轻量规整，去除残留噪声
        return repairMarkdownStructure(cleanText(markdown));
    }

    /**
     * 对原生 Markdown 执行最小破坏清洗。
     *
     * <p>该路径不做 HTML 重解析或 Markdown 重新序列化，只处理跨平台换行、不可见字符、
     * 明显文件噪音和危险 raw HTML 标签，避免破坏标题、表格、列表缩进和代码块围栏。
     *
     * @param rawMarkdown 原生 Markdown 文本
     * @return 最小规整后的 Markdown
     */
    public String cleanNativeMarkdown(String rawMarkdown) {
        // 空值守卫：上游可能传入 null 或空字符串
        if (rawMarkdown == null || rawMarkdown.isBlank()) {
            return "";
        }
        // 第 1 步：归一化换行符（CRLF/CR → LF）
        String text = normalizeCompatibilityChars(rawMarkdown.replace("\r\n", "\n").replace("\r", "\n"));
        // 第 2 步：去除控制字符（保留 \n 和 \t）
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        // 第 3 步：去除零宽空格/BOM 等不可见格式化字符
        text = INVISIBLE_FORMATTING_CHARS.matcher(text).replaceAll("");
        // 第 4 步：按行遍历，对代码块内外分别处理
        String[] lines = text.split("\n", -1);
        StringBuilder cleaned = new StringBuilder(text.length());
        boolean inFencedCodeBlock = false;
        boolean inDangerousHtmlBlock = false;
        String dangerousHtmlEndTag = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!inFencedCodeBlock && inDangerousHtmlBlock) {
                DangerousHtmlLineResult htmlCleaned =
                        cleanDangerousHtmlOutsideCode(line, true, dangerousHtmlEndTag);
                inDangerousHtmlBlock = htmlCleaned.inDangerousHtmlBlock();
                dangerousHtmlEndTag = htmlCleaned.dangerousHtmlEndTag();
                cleaned.append(cleanNativeMarkdownLine(htmlCleaned.line()));
            } else if (isFencedCodeDelimiter(line)) {
                // 遇到围栏分隔符：切换代码块状态，仅去除行尾空白
                inFencedCodeBlock = !inFencedCodeBlock;
                cleaned.append(stripTrailingWhitespace(line));
            } else if (inFencedCodeBlock || isIndentedCodeLine(line)) {
                // 代码块内或缩进代码行：仅去除行尾空白，保留内部格式
                cleaned.append(stripTrailingWhitespace(line));
            } else {
                // 普通行：执行原生 Markdown 的最小破坏清洗
                DangerousHtmlLineResult htmlCleaned =
                        cleanDangerousHtmlOutsideCode(line, inDangerousHtmlBlock, dangerousHtmlEndTag);
                inDangerousHtmlBlock = htmlCleaned.inDangerousHtmlBlock();
                dangerousHtmlEndTag = htmlCleaned.dangerousHtmlEndTag();
                cleaned.append(cleanNativeMarkdownLine(htmlCleaned.line()));
            }
            if (i < lines.length - 1) {
                cleaned.append('\n');
            }
        }

        // 第 7 步：压缩过多连续空行（3+ → 2），保持 Markdown 可读性
        return cleaned.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * 对 Markdown/纯文本执行轻量规整。
     *
     * @param rawText 原始文本
     * @return 规整后的文本
     */
    public String cleanText(String rawText) {
        // 空值守卫：上游可能传入 null 或空字符串
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        // 第 1 步：统一换行符为 Unix 风格（\n）
        String text = normalizeCompatibilityChars(rawText.replace("\r\n", "\n").replace("\r", "\n"));
        // 第 2 步：去除除换行/制表外的所有控制字符
        text = CONTROL_CHARS.matcher(text).replaceAll("");

        // 第 3 步：按行遍历，区分代码块内外
        String[] lines = text.split("\n", -1);
        StringBuilder cleaned = new StringBuilder(text.length());
        boolean inFencedCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isFencedCodeDelimiter(line)) {
                // 遇到围栏分隔符：切换代码块状态，原样保留分隔符行
                inFencedCodeBlock = !inFencedCodeBlock;
                cleaned.append(line);
            } else if (inFencedCodeBlock || isIndentedCodeLine(line)) {
                // 代码块仅做控制字符与换行归一化，不压缩内部空格和缩进。
                cleaned.append(line);
            } else {
                // 普通行：执行完整清洗（去图片/URL/分隔线 + 合并空白）
                cleaned.append(cleanRegularLine(line));
            }
            if (i < lines.length - 1) {
                cleaned.append('\n');
            }
        }

        // 第 3 步：将三个及以上的连续空行压缩为两个空行，保持 Markdown 可读性
        return cleaned.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * 对普通行（非代码块内）执行轻量规整。
     *
     * <p>按顺序执行以下清洗步骤：
     * <ol>
     *   <li>移除独立成行的图片文件名（如 image1.png）；</li>
     *   <li>移除行内图片 URL；</li>
     *   <li>移除行内本地文件 URL；</li>
     *   <li>移除无意义分隔线（连续横线/下划线/等号）；</li>
     *   <li>合并连续空格和制表符为单个空格。</li>
     * </ol>
     *
     * @param line 待清洗的普通行
     * @return 规整后的行
     */
    private static String cleanRegularLine(String line) {
        String cleaned = IMAGE_FILENAME_LINE.matcher(line).replaceAll("");
        cleaned = IMAGE_URL.matcher(cleaned).replaceAll("");
        cleaned = FILE_URL.matcher(cleaned).replaceAll("");
        cleaned = PAGE_CHROME_NOISE_LINE.matcher(cleaned).replaceAll("");
        cleaned = SEPARATOR_LINE.matcher(cleaned).replaceAll("");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        return stripTrailingWhitespace(cleaned);
    }

    private static MutableDataSet htmlToMarkdownOptions() {
        MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
        options.set(FlexmarkHtmlConverter.UNORDERED_LIST_DELIMITER, '-');
        return options;
    }

    private static String repairMarkdownStructure(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<String> repaired = new ArrayList<>(lines.length);
        String pendingTableSeparator = null;

        for (String line : lines) {
            String normalizedLine = normalizeWordBullet(line);
            if (pendingTableSeparator != null) {
                if (isMarkdownTableRow(normalizedLine) && !isMarkdownTableSeparator(normalizedLine)) {
                    repaired.add(normalizedLine);
                    repaired.add(pendingTableSeparator);
                    pendingTableSeparator = null;
                    continue;
                }
                repaired.add(pendingTableSeparator);
                pendingTableSeparator = null;
            }

            if (isMarkdownTableSeparator(normalizedLine)
                    && (repaired.isEmpty() || !isMarkdownTableRow(repaired.getLast()))) {
                pendingTableSeparator = normalizedLine;
                continue;
            }

            repaired.add(normalizedLine);
        }
        if (pendingTableSeparator != null) {
            repaired.add(pendingTableSeparator);
        }
        return String.join("\n", repaired).replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String normalizeWordBullet(String line) {
        Matcher matcher = WORD_BULLET_LINE.matcher(line);
        if (matcher.matches()) {
            return "- " + matcher.group(1).trim();
        }
        return line;
    }

    private static boolean isMarkdownTableRow(String line) {
        return MARKDOWN_TABLE_ROW.matcher(line).matches();
    }

    private static boolean isMarkdownTableSeparator(String line) {
        return MARKDOWN_TABLE_SEPARATOR.matcher(line).matches();
    }

    /**
     * 对原生 Markdown 行（非代码块内、非缩进代码）执行最小破坏清洗。
     *
     * <p>与 {@link #cleanRegularLine} 的区别在于：
     * <ul>
     *   <li>不压缩空格（保留 Markdown 缩进语义）；</li>
     *   <li>使用更严格的图片/文件 URL 行级匹配（整行匹配才移除）；</li>
     *   <li>不处理分隔线（避免误删 Markdown 的 setext 标题）。</li>
     * </ul>
     *
     * @param line 待清洗的原生 Markdown 行
     * @return 规整后的行
     */
    private static String cleanNativeMarkdownLine(String line) {
        String cleaned = IMAGE_FILENAME_LINE.matcher(line).replaceAll("");
        cleaned = IMAGE_URL_LINE.matcher(cleaned).replaceAll("");
        cleaned = FILE_URL_LINE.matcher(cleaned).replaceAll("");
        cleaned = PAGE_CHROME_NOISE_LINE.matcher(cleaned).replaceAll("");
        return stripTrailingWhitespace(cleaned);
    }

    private static String normalizeCompatibilityChars(String text) {
        return text
                .replace('⼀', '一')
                .replace('⼆', '二')
                .replace('⼈', '人')
                .replace('⼊', '入')
                .replace('⼯', '工')
                .replace('⼦', '子')
                .replace('⽂', '文')
                .replace('⾳', '音')
                .replace('⽤', '用')
                .replace('⾏', '行')
                .replace('⾃', '自')
                .replace('⾝', '身')
                .replace('⽗', '父')
                .replace('⽽', '而')
                .replace('⻚', '页');
    }

    /**
     * 清理代码块外的危险 HTML 内容（支持跨行块）。
     *
     * <p>处理策略分三个阶段：
     * <ol>
     *   <li><b>块内续行</b>：若当前处于危险 HTML 块内部，
     *       查找闭标签并截断；整行都在块内则返回空字符串；</li>
     *   <li><b>单行块移除</b>：移除行内完整闭合的危险 HTML 标签对
     *       （如 {@code <script>...</script>}）；</li>
     *   <li><b>跨行块起点检测</b>：发现危险开标签但本行无闭标签时，
     *       截断后续内容并标记进入块状态。</li>
     * </ol>
     *
     * <p>该方法仅在非围栏代码块内调用，代码块内保留原始内容不做处理。
     *
     * @param line                 当前文本行
     * @param inDangerousHtmlBlock 是否已处于危险 HTML 块内部
     * @param dangerousHtmlEndTag  期待匹配的闭标签（如 {@code </script>}）
     * @return 包含清洗后行内容、块状态和闭标签的结果对象
     */
    private static DangerousHtmlLineResult cleanDangerousHtmlOutsideCode(
            String line,
            boolean inDangerousHtmlBlock,
            String dangerousHtmlEndTag) {
        String cleaned = line;
        boolean stillInBlock = inDangerousHtmlBlock;
        String endTag = dangerousHtmlEndTag;

        // 阶段 1：块内续行 —— 查找期待的闭标签
        if (stillInBlock) {
            // 大小写不敏感查找闭标签（如 </script>、</SCRIPT>）
            int endIndex = indexOfIgnoreCase(cleaned, endTag);
            if (endIndex < 0) {
                // 整行都在危险块内，全部丢弃
                return new DangerousHtmlLineResult("", true, endTag);
            }
            // 找到闭标签，截取其后内容继续处理
            cleaned = cleaned.substring(endIndex + endTag.length());
            stillInBlock = false;
            endTag = null;
        }

        // 阶段 2：单行块移除 —— 移除完整危险标签对
        cleaned = DANGEROUS_HTML_BLOCK.matcher(cleaned).replaceAll("");
        // 阶段 3：跨行块起点检测 —— 发现危险开标签
        Matcher openTag = DANGEROUS_HTML_OPEN_TAG.matcher(cleaned);
        if (openTag.find()) {
            // 根据开标签构造对应的闭标签（如 <script> → </script>）
            endTag = "</" + openTag.group(1) + ">";
            // 丢弃开标签及其后内容，标记进入块状态
            cleaned = cleaned.substring(0, openTag.start());
            stillInBlock = true;
        }
        // 移除残留的孤立危险单标签
        cleaned = DANGEROUS_HTML_TAG.matcher(cleaned).replaceAll("");
        return new DangerousHtmlLineResult(cleaned, stillInBlock, endTag);
    }

    /**
     * 大小写不敏感的字符串查找（不使用正则，避免转义问题）。
     *
     * <p>用于在文本中定位危险 HTML 闭标签（如 {@code </script>}），
     * 使用 {@link String#regionMatches} 逐位置比较，
     * 性能优于正则且无需担心标签名中的特殊字符。
     *
     * @param text   待搜索的文本
     * @param target 目标字符串（如 {@code </script>}）
     * @return 首次匹配的起始索引，未找到返回 -1
     */
    private static int indexOfIgnoreCase(String text, String target) {
        // 空值守卫：目标为空直接返回未找到
        if (target == null || target.isEmpty()) {
            return -1;
        }
        // 逐位置滑动窗口比较，窗口大小等于 target 长度
        int max = text.length() - target.length();
        for (int i = 0; i <= max; i++) {
            // regionMatches(true, ...) 第三个参数 true 表示忽略大小写
            if (text.regionMatches(true, i, target, 0, target.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 危险 HTML 行清洗结果（内部 Record）。
     *
     * <p>封装 {@link #cleanDangerousHtmlOutsideCode} 的三项输出：
     * <ul>
     *   <li>{@code line}：清洗后的行内容（可能为空字符串）；</li>
     *   <li>{@code inDangerousHtmlBlock}：处理完成后是否仍处于危险块内；</li>
     *   <li>{@code dangerousHtmlEndTag}：期待的闭标签（如 {@code </script>}），
     *       不在块内时为 null。</li>
     * </ul>
     *
     * @param line                 清洗后的行内容
     * @param inDangerousHtmlBlock 是否仍处于危险 HTML 块内部
     * @param dangerousHtmlEndTag  期待的闭标签（块外为 null）
     */
    private record DangerousHtmlLineResult(
            String line,
            boolean inDangerousHtmlBlock,
            String dangerousHtmlEndTag) {
    }

    /**
     * 判断当前行是否为围栏代码块分隔符。
     *
     * <p>围栏代码块分隔符是指以 3 个及以上反引号（{@code ```}）或
     * 波浪号（{@code ~~~}）开头的行，用于标记 Markdown 代码块的起止边界。
     * 代码块内部内容（如缩进、空格）需要保留原样，不做规整处理。
     *
     * @param line 待判断的文本行
     * @return {@code true} 如果该行是围栏代码块分隔符
     */
    private static boolean isFencedCodeDelimiter(String line) {
        return FENCED_CODE_DELIMITER.matcher(line).matches();
    }

    /**
     * 判断当前行是否为缩进代码行。
     *
     * <p>Markdown 规范中，以 4 个空格或 1 个制表符开头的行被视为缩进代码块。
     * 此类行在文本清洗时应保留原始格式（空格、缩进等），不做压缩或规整处理，
     * 以保持代码片段的完整性。
     *
     * @param line 待判断的文本行
     * @return {@code true} 如果该行以 4 个空格或 1 个制表符开头
     */
    private static boolean isIndentedCodeLine(String line) {
        return line.startsWith("    ") || line.startsWith("\t");
    }

    /**
     * 去除行尾空白字符（空格和制表符）。
     *
     * <p>从行尾向前扫描，找到第一个非空白字符后截断。该方法直接操作字符数组，
     * 避免创建不必要的中间字符串对象，适用于大规模文本的逐行处理场景。
     *
     * @param line 待处理的文本行
     * @return 去除行尾空白后的行内容，如果整行均为空白则返回空字符串
     */
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
