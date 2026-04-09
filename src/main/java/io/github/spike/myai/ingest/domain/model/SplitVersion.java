package io.github.spike.myai.ingest.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分块版本工具类。
 *
 * <p>规则：使用 "vN" 形式（例如 v1/v2）。
 * <p>如果传入非法版本，统一回退为 v1。
 */
public final class SplitVersion {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^v(\\d+)$");

    private SplitVersion() {
    }

    public static String next(String current) {
        // 空值或非法值时，从 v1 开始。
        if (current == null || current.isBlank()) {
            return "v1";
        }
        Matcher matcher = VERSION_PATTERN.matcher(current.trim());
        if (!matcher.matches()) {
            return "v1";
        }
        // 合法 vN 版本递增。
        int version = Integer.parseInt(matcher.group(1));
        return "v" + (version + 1);
    }
}
