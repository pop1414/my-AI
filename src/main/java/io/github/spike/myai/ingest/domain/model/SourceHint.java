package io.github.spike.myai.ingest.domain.model;

/**
 * chunk 来源上下文。
 *
 * <p>当前对外仍序列化为 {@code {"heading":"..."}} 字符串；内部统一通过该 value object
 * 管理字段名、转义规则和空值语义。
 */
public record SourceHint(String heading, String rawValue) {

    private static final SourceHint EMPTY = new SourceHint(null, null);
    private static final String HEADING_PREFIX = "{\"heading\":\"";
    private static final String JSON_SUFFIX = "\"}";

    public SourceHint {
        heading = blankToNull(heading);
        rawValue = blankToNull(rawValue);
    }

    public static SourceHint none() {
        return EMPTY;
    }

    public static SourceHint heading(String heading) {
        if (heading == null || heading.isBlank()) {
            return EMPTY;
        }
        return new SourceHint(heading, null);
    }

    public static SourceHint fromStorageValue(String value) {
        if (value == null || value.isBlank()) {
            return EMPTY;
        }
        String trimmed = value.trim();
        String heading = decodeHeadingJson(trimmed);
        if (heading != null) {
            return heading(heading);
        }
        return new SourceHint(null, trimmed);
    }

    public boolean isEmpty() {
        return heading == null && rawValue == null;
    }

    public String toStorageValue() {
        if (rawValue != null) {
            return rawValue;
        }
        if (heading == null) {
            return null;
        }
        return HEADING_PREFIX + escapeJson(heading) + JSON_SUFFIX;
    }

    private static String decodeHeadingJson(String value) {
        if (!value.startsWith(HEADING_PREFIX) || !value.endsWith(JSON_SUFFIX)) {
            return null;
        }
        String encoded = value.substring(HEADING_PREFIX.length(), value.length() - JSON_SUFFIX.length());
        return unescapeJson(encoded);
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

    private static String unescapeJson(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        if (escaped) {
            sb.append('\\');
        }
        return sb.toString();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
