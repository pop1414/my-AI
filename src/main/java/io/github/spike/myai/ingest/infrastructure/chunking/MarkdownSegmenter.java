package io.github.spike.myai.ingest.infrastructure.chunking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 cleaned.md 文本归一化为结构片段和 token 序列。
 */
final class MarkdownSegmenter {

    List<Segment> segment(String text) {
        return Arrays.stream(normalize(text).split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .map(segment -> new Segment(segment, tokenize(segment)))
                .toList();
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static List<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    record Segment(String text, List<String> tokens) {
        String firstLine() {
            String trimmed = text.strip();
            int idx = trimmed.indexOf('\n');
            if (idx >= 0) {
                return trimmed.substring(0, idx).trim();
            }
            return trimmed;
        }
    }
}
