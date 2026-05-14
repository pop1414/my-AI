package io.github.spike.myai.ingest.infrastructure.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构修复 Module。
 */
final class MarkdownStructureRepairer {

    private static final Pattern WORD_BULLET_LINE = Pattern.compile("^([\\s\\u00A0]*)[·•]\\s+(.+)$");
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.+$");
    private static final Pattern CHINESE_NUMBERED_HEADING =
            Pattern.compile("^第[\\p{IsHan}0-9]+[章节篇]\\s+[^，。！？,.!?]{1,32}$");
    private static final Pattern GLUED_CHINESE_HEADING =
            Pattern.compile("^(第[\\p{IsHan}0-9]+[章节篇]\\s+\\S{2,24}?)(\\s+(当|如果|在|为了|这|该|请|不要|而应|通常|正文|系统|用户)\\S.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+).+$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^\\s*>.*$");
    private static final Pattern MARKDOWN_TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern MARKDOWN_TABLE_SEPARATOR =
            Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");

    private MarkdownStructureRepairer() {
    }

    static String repair(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<String> repaired = new ArrayList<>(lines.length);
        String pendingTableSeparator = null;
        boolean inFencedCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String nextLine = i + 1 < lines.length ? lines[i + 1] : null;
            if (MarkdownTextCleaner.isFencedCodeDelimiter(line)) {
                if (pendingTableSeparator != null) {
                    repaired.add(pendingTableSeparator);
                    pendingTableSeparator = null;
                }
                inFencedCodeBlock = !inFencedCodeBlock;
                repaired.add(line);
                continue;
            }
            if (inFencedCodeBlock || MarkdownTextCleaner.isIndentedCodeLine(line)) {
                if (pendingTableSeparator != null) {
                    repaired.add(pendingTableSeparator);
                    pendingTableSeparator = null;
                }
                repaired.add(line);
                continue;
            }
            if (isPotentialHtmlPreCodeLeadIn(line, nextLine)) {
                if (pendingTableSeparator != null) {
                    repaired.add(pendingTableSeparator);
                    pendingTableSeparator = null;
                }
                repaired.add(line);
                continue;
            }

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

            addLogicalLine(repaired, normalizedLine);
        }
        if (pendingTableSeparator != null) {
            repaired.add(pendingTableSeparator);
        }
        return repairSoftWrappedParagraphs(repaired).replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String normalizeWordBullet(String line) {
        Matcher matcher = WORD_BULLET_LINE.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).replace('\u00A0', ' ') + "- " + matcher.group(2).trim();
        }
        return line;
    }

    private static void addLogicalLine(List<String> repaired, String line) {
        Matcher gluedHeading = GLUED_CHINESE_HEADING.matcher(line);
        if (gluedHeading.matches()) {
            repaired.add(gluedHeading.group(1).trim());
            repaired.add(gluedHeading.group(2).trim());
            return;
        }
        repaired.add(line);
    }

    private static String repairSoftWrappedParagraphs(List<String> lines) {
        List<String> repaired = new ArrayList<>(lines.size());
        StringBuilder paragraph = new StringBuilder();
        String previousParagraphLine = "";
        boolean inFencedCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (MarkdownTextCleaner.isFencedCodeDelimiter(line)) {
                flushParagraph(repaired, paragraph);
                inFencedCodeBlock = !inFencedCodeBlock;
                repaired.add(line);
                previousParagraphLine = "";
                continue;
            }
            if (inFencedCodeBlock) {
                repaired.add(line);
                continue;
            }
            if (trimmed.isEmpty()) {
                flushParagraph(repaired, paragraph);
                repaired.add("");
                previousParagraphLine = "";
                continue;
            }
            if (isStructuralLine(line)) {
                flushParagraph(repaired, paragraph);
                repaired.add(line);
                previousParagraphLine = "";
                continue;
            }
            if (!paragraph.isEmpty() && startsLikelyNewParagraph(previousParagraphLine, trimmed)) {
                flushParagraph(repaired, paragraph);
            }
            appendParagraphLine(paragraph, trimmed);
            previousParagraphLine = trimmed;
        }

        flushParagraph(repaired, paragraph);
        return String.join("\n", repaired);
    }

    private static boolean isStructuralLine(String line) {
        String trimmed = line.trim();
        return MARKDOWN_HEADING.matcher(trimmed).matches()
                || CHINESE_NUMBERED_HEADING.matcher(trimmed).matches()
                || LIST_ITEM.matcher(line).matches()
                || BLOCKQUOTE.matcher(line).matches()
                || isMarkdownTableRow(trimmed)
                || isMarkdownTableSeparator(trimmed)
                || MarkdownTextCleaner.isFencedCodeDelimiter(line)
                || MarkdownTextCleaner.isIndentedCodeLine(line);
    }

    private static boolean startsLikelyNewParagraph(String previousLine, String currentLine) {
        if (previousLine.isEmpty() || currentLine.isEmpty()) {
            return false;
        }
        char last = previousLine.charAt(previousLine.length() - 1);
        return isTerminalPunctuation(last)
                && (startsLikeBodyLead(currentLine) || currentLine.length() >= 16);
    }

    private static boolean startsLikeBodyLead(String line) {
        return line.startsWith("如果")
                || line.startsWith("为了")
                || line.startsWith("当")
                || line.startsWith("在")
                || line.startsWith("这")
                || line.startsWith("该")
                || line.startsWith("请")
                || line.startsWith("不要")
                || line.startsWith("而应")
                || line.startsWith("通常")
                || line.startsWith("正文")
                || line.startsWith("系统")
                || line.startsWith("用户")
                || line.startsWith("If ")
                || line.startsWith("When ")
                || line.startsWith("This ")
                || line.startsWith("These ");
    }

    private static boolean isTerminalPunctuation(char ch) {
        return ch == '。' || ch == '！' || ch == '？' || ch == '.' || ch == '!' || ch == '?';
    }

    private static void appendParagraphLine(StringBuilder paragraph, String line) {
        if (paragraph.isEmpty()) {
            paragraph.append(line);
            return;
        }
        if (needsJoinSpace(paragraph.charAt(paragraph.length() - 1), line.charAt(0))) {
            paragraph.append(' ');
        }
        paragraph.append(line);
    }

    private static boolean needsJoinSpace(char left, char right) {
        if (Character.isWhitespace(left) || Character.isWhitespace(right)) {
            return false;
        }
        if (isCjk(left) || isCjk(right)) {
            return false;
        }
        return ",.;:!?，。；：！？、)]}）】》".indexOf(right) < 0
                && "([{（【《".indexOf(left) < 0;
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeScript script = Character.UnicodeScript.of(ch);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static void flushParagraph(List<String> repaired, StringBuilder paragraph) {
        if (!paragraph.isEmpty()) {
            repaired.add(paragraph.toString());
            paragraph.setLength(0);
        }
    }

    private static boolean isPotentialHtmlPreCodeLeadIn(String line, String nextLine) {
        return nextLine != null
                && MarkdownTextCleaner.isIndentedCodeLine(nextLine)
                && WORD_BULLET_LINE.matcher(line).matches();
    }

    private static boolean isMarkdownTableRow(String line) {
        return MARKDOWN_TABLE_ROW.matcher(line).matches();
    }

    private static boolean isMarkdownTableSeparator(String line) {
        return MARKDOWN_TABLE_SEPARATOR.matcher(line).matches();
    }
}
