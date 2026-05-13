package io.github.spike.myai.ingest.infrastructure.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别 Markdown、中文编号和清洗后独立短标题上下文。
 */
final class HeadingContextExtractor {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern CHINESE_HEADING = Pattern.compile("^(第[\\p{IsHan}0-9]+[章节篇])\\s*(.*)$");
    private static final Pattern NUMBER_HEADING = Pattern.compile("^\\d+(?:\\.\\d+)*\\s+.+$");
    private static final Pattern PLAIN_STANDALONE_HEADING =
            Pattern.compile("^[\\p{IsHan}A-Za-z0-9][\\p{IsHan}A-Za-z0-9\\s/（）()：:-]{1,47}$");

    List<HeadingSegment> extract(List<MarkdownSegmenter.Segment> segments) {
        List<HeadingSegment> headingSegments = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            MarkdownSegmenter.Segment segment = segments.get(i);
            MarkdownSegmenter.Segment nextSegment = i + 1 < segments.size() ? segments.get(i + 1) : null;
            headingSegments.add(new HeadingSegment(segment, extractHeading(segment, nextSegment)));
        }
        return headingSegments;
    }

    private static String extractHeading(
            MarkdownSegmenter.Segment segment, MarkdownSegmenter.Segment nextSegment) {
        String firstLine = segment.firstLine();
        Matcher markdown = MARKDOWN_HEADING.matcher(firstLine);
        if (markdown.matches()) {
            return markdown.group(1).trim();
        }
        Matcher chinese = CHINESE_HEADING.matcher(firstLine);
        if (chinese.matches()) {
            return firstLine.trim();
        }
        Matcher number = NUMBER_HEADING.matcher(firstLine);
        if (number.matches()) {
            return firstLine.trim();
        }
        if (isPlainStandaloneHeading(segment, firstLine, nextSegment)) {
            return firstLine.trim();
        }
        return null;
    }

    private static boolean isPlainStandaloneHeading(
            MarkdownSegmenter.Segment segment, String firstLine, MarkdownSegmenter.Segment nextSegment) {
        String trimmed = segment.text().strip();
        if (!trimmed.equals(firstLine) || firstLine.length() > 48) {
            return false;
        }
        if (firstLine.startsWith("* ")
                || firstLine.startsWith("- ")
                || firstLine.startsWith("|")
                || firstLine.startsWith("```")
                || firstLine.endsWith("。")
                || firstLine.endsWith("！")
                || firstLine.endsWith("？")
                || firstLine.endsWith(".")
                || firstLine.endsWith(",")
                || startsLikeBodySentence(firstLine)) {
            return false;
        }
        if (!PLAIN_STANDALONE_HEADING.matcher(firstLine).matches()) {
            return false;
        }
        return nextSegment != null
                && looksLikeFollowingContent(nextSegment)
                && !looksLikePlainStandaloneHeading(nextSegment);
    }

    private static boolean startsLikeBodySentence(String line) {
        return line.startsWith("面向")
                || line.startsWith("如果")
                || line.startsWith("请")
                || line.startsWith("先")
                || line.startsWith("再")
                || line.startsWith("为了")
                || line.startsWith("当")
                || line.startsWith("在")
                || line.startsWith("我们")
                || line.startsWith("用户")
                || line.startsWith("系统")
                || line.startsWith("该")
                || line.startsWith("这")
                || line.startsWith("这些")
                || line.startsWith("以下")
                || line.startsWith("This ")
                || line.startsWith("These ")
                || line.startsWith("If ")
                || line.startsWith("When ")
                || line.startsWith("Please ");
    }

    private static boolean looksLikeFollowingContent(MarkdownSegmenter.Segment segment) {
        String trimmed = segment.text().strip();
        return trimmed.contains("\n")
                || trimmed.startsWith("* ")
                || trimmed.startsWith("- ")
                || trimmed.startsWith("|")
                || trimmed.startsWith("```")
                || trimmed.endsWith("。")
                || trimmed.endsWith("！")
                || trimmed.endsWith("？")
                || trimmed.endsWith(".")
                || trimmed.endsWith(",");
    }

    private static boolean looksLikePlainStandaloneHeading(MarkdownSegmenter.Segment segment) {
        String firstLine = segment.firstLine();
        String trimmed = segment.text().strip();
        return trimmed.equals(firstLine)
                && firstLine.length() <= 48
                && !startsLikeBodySentence(firstLine)
                && PLAIN_STANDALONE_HEADING.matcher(firstLine).matches();
    }

    record HeadingSegment(MarkdownSegmenter.Segment segment, String heading) {}
}
