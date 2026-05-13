package io.github.spike.myai.ingest.infrastructure.chunking;

/**
 * 将 chunk 来源上下文编码为 sourceHint JSON。
 */
final class SourceHintEncoder {

    String encodeHeading(String heading) {
        if (heading == null || heading.isBlank()) {
            return null;
        }
        return "{\"heading\":\"" + escapeJson(heading) + "\"}";
    }

    private static String escapeJson(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
