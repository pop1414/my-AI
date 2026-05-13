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
            return matcher.group(1).replace('\u00A0', ' ') + "- " + matcher.group(2).trim();
        }
        return line;
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
