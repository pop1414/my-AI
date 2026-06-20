package io.github.spike.myai.ingest.infrastructure.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown/纯文本行级清洗 Module。
 *
 * <p>Docling 迁移后仅保留 {@link #cleanNativeMarkdown} 路径，
 * 用于对 Docling 产出的原生 Markdown 执行最小破坏清洗。
 */
final class MarkdownTextCleaner {

    private static final Pattern FENCED_CODE_DELIMITER = Pattern.compile("^\\s{0,3}(```+|~~~+).*$");
    private static final Pattern DANGEROUS_HTML_BLOCK =
            Pattern.compile("(?is)<(script|style|iframe|object|embed|applet)\\b[^>]*>.*?</\\1>");
    private static final Pattern DANGEROUS_HTML_TAG =
            Pattern.compile("(?is)</?(script|style|iframe|object|embed|applet)\\b[^>]*>");
    private static final Pattern DANGEROUS_HTML_OPEN_TAG =
            Pattern.compile("(?i)<(script|style|iframe|object|embed|applet)\\b[^>]*>");
    private static final Pattern INVISIBLE_FORMATTING_CHARS = Pattern.compile("[\\uFEFF\\u200B\\u200C\\u200D]");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[([^\\]]*)\\]\\((?:<)?((?:data:image/[^\\s>]+)"
                    + "|(?:file:///\\S+)"
                    + "|(?:https?://\\S+\\.(?:png|jpg|jpeg|gif|bmp|webp)(?:\\?\\S*)?)"
                    + "|(?:[^)\\s]+\\.(?:png|jpg|jpeg|gif|bmp|webp)(?:\\?\\S*)?))(?:>)?\\)");
    private static final Pattern HTML_IMAGE_TAG = Pattern.compile("(?i)<img\\b[^>]*>");
    private static final Pattern HTML_IMAGE_ALT = Pattern.compile("(?i)\\balt\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern GENERIC_IMAGE_ALT_TEXT =
            Pattern.compile("(?i)^(image|img|picture|photo|figure|图片|图像|插图)$");
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?im)^\\s*image\\d+\\.(png|jpg|jpeg|gif|bmp|webp)\\s*$");
    private static final Pattern IMAGE_URL_LINE =
            Pattern.compile("(?im)^\\s*https?://\\S+\\.(png|jpg|jpeg|gif|bmp|webp)(\\?\\S*)?\\s*$");
    private static final Pattern DATA_IMAGE_URL_LINE = Pattern.compile("(?im)^\\s*data:image/\\S+\\s*$");
    private static final Pattern FILE_URL_LINE = Pattern.compile("(?im)^\\s*file:///\\S+\\s*$");
    private static final Pattern PAGE_CHROME_NOISE_LINE =
            Pattern.compile("(?im)^\\s*(内部评审稿|第\\s*\\d+\\s*页\\s*(?:/\\s*质检热线\\s*[-0-9]+)?)\\s*$");

    String cleanNativeMarkdown(String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.isBlank()) {
            return "";
        }

        String text = normalizeCompatibilityChars(rawMarkdown.replace("\r\n", "\n").replace("\r", "\n"));
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = INVISIBLE_FORMATTING_CHARS.matcher(text).replaceAll("");

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
                inFencedCodeBlock = !inFencedCodeBlock;
                cleaned.append(stripTrailingWhitespace(line));
            } else if (inFencedCodeBlock || isIndentedCodeLine(line)) {
                cleaned.append(stripTrailingWhitespace(line));
            } else {
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

        return cleaned.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String cleanNativeMarkdownLine(String line) {
        String cleaned = replaceMarkdownImagesWithAltText(line);
        cleaned = replaceHtmlImageWithAltText(cleaned);
        cleaned = IMAGE_FILENAME_LINE.matcher(cleaned).replaceAll("");
        cleaned = IMAGE_URL_LINE.matcher(cleaned).replaceAll("");
        cleaned = DATA_IMAGE_URL_LINE.matcher(cleaned).replaceAll("");
        cleaned = FILE_URL_LINE.matcher(cleaned).replaceAll("");
        cleaned = PAGE_CHROME_NOISE_LINE.matcher(cleaned).replaceAll("");
        return stripTrailingWhitespace(cleaned);
    }

    private static String replaceMarkdownImagesWithAltText(String line) {
        Matcher matcher = MARKDOWN_IMAGE.matcher(line);
        StringBuffer cleaned = new StringBuffer(line.length());
        while (matcher.find()) {
            String replacement = normalizeImageAltText(matcher.group(1));
            matcher.appendReplacement(cleaned, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    private static String replaceHtmlImageWithAltText(String line) {
        Matcher matcher = HTML_IMAGE_TAG.matcher(line);
        StringBuffer cleaned = new StringBuffer(line.length());
        while (matcher.find()) {
            String imageTag = matcher.group();
            Matcher altMatcher = HTML_IMAGE_ALT.matcher(imageTag);
            String replacement = "";
            if (altMatcher.find()) {
                replacement = normalizeImageAltText(altMatcher.group(2));
            }
            matcher.appendReplacement(cleaned, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    private static String normalizeImageAltText(String altText) {
        if (altText == null) {
            return "";
        }
        String normalized = altText.trim();
        if (normalized.isBlank() || GENERIC_IMAGE_ALT_TEXT.matcher(normalized).matches()) {
            return "";
        }
        return normalized;
    }

    private static DangerousHtmlLineResult cleanDangerousHtmlOutsideCode(
            String line,
            boolean inDangerousHtmlBlock,
            String dangerousHtmlEndTag) {
        String cleaned = line;
        boolean stillInBlock = inDangerousHtmlBlock;
        String endTag = dangerousHtmlEndTag;

        if (stillInBlock) {
            int endIndex = indexOfIgnoreCase(cleaned, endTag);
            if (endIndex < 0) {
                return new DangerousHtmlLineResult("", true, endTag);
            }
            cleaned = cleaned.substring(endIndex + endTag.length());
            stillInBlock = false;
            endTag = null;
        }

        cleaned = DANGEROUS_HTML_BLOCK.matcher(cleaned).replaceAll("");
        Matcher openTag = DANGEROUS_HTML_OPEN_TAG.matcher(cleaned);
        if (openTag.find()) {
            endTag = "</" + openTag.group(1) + ">";
            cleaned = cleaned.substring(0, openTag.start());
            stillInBlock = true;
        }
        cleaned = DANGEROUS_HTML_TAG.matcher(cleaned).replaceAll("");
        return new DangerousHtmlLineResult(cleaned, stillInBlock, endTag);
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

    private static int indexOfIgnoreCase(String text, String target) {
        if (target == null || target.isEmpty()) {
            return -1;
        }
        int max = text.length() - target.length();
        for (int i = 0; i <= max; i++) {
            if (text.regionMatches(true, i, target, 0, target.length())) {
                return i;
            }
        }
        return -1;
    }

    static boolean isFencedCodeDelimiter(String line) {
        return FENCED_CODE_DELIMITER.matcher(line).matches();
    }

    static boolean isIndentedCodeLine(String line) {
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

    private record DangerousHtmlLineResult(
            String line,
            boolean inDangerousHtmlBlock,
            String dangerousHtmlEndTag) {
    }
}
