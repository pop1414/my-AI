package io.github.spike.myai.ingest.infrastructure.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * 构建文档处理元数据 JSON。
 */
final class ProcessingMetadataBuilder {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    private static final List<String> PAGE_COUNT_KEYS = List.of("xmpTPg:NPages", "meta:page-count", "Page-Count");

    private final ObjectMapper objectMapper;

    ProcessingMetadataBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String build(String filename, Metadata metadata, String cleanedMarkdown) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "v1");
        root.put("stable", buildStableMetadata(filename, metadata));

        Map<String, Object> conditional = buildConditionalMetadata(metadata, cleanedMarkdown);
        if (!conditional.isEmpty()) {
            root.put("conditional", conditional);
        }

        root.put("best_effort", Map.of());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize processing metadata", ex);
        }
    }

    private static Map<String, Object> buildStableMetadata(String filename, Metadata metadata) {
        Map<String, Object> stable = new LinkedHashMap<>();
        stable.put("source_file", filename);
        stable.put("file_ext", DocumentParserRouter.fileExtension(filename));
        stable.put("mime_type", firstNonBlank(metadata.get(Metadata.CONTENT_TYPE), "application/octet-stream"));
        stable.put("quality", "high");
        stable.put("created_at", Instant.now().toString());
        return stable;
    }

    private static Map<String, Object> buildConditionalMetadata(Metadata metadata, String cleanedMarkdown) {
        Map<String, Object> conditional = new LinkedHashMap<>();
        String language = firstNonBlank(metadata.get("language"), metadata.get(Metadata.CONTENT_LANGUAGE));
        if (language != null) {
            conditional.put("language", language);
        }

        Integer pageCount = parsePageCount(metadata);
        if (pageCount != null) {
            conditional.put("page_count", pageCount);
        }

        String title = firstNonBlank(metadata.get(TikaCoreProperties.TITLE));
        if (title != null) {
            conditional.put("primary_title", title);
        }

        List<String> titleOutlineSample = extractTitleOutlineSample(cleanedMarkdown);
        if (!titleOutlineSample.isEmpty()) {
            if (title == null) {
                conditional.put("primary_title", titleOutlineSample.getFirst());
            }
            conditional.put("title_outline_sample", titleOutlineSample);
        }
        return conditional;
    }

    private static Integer parsePageCount(Metadata metadata) {
        for (String key : PAGE_COUNT_KEYS) {
            String raw = metadata.get(key);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                // Try the next metadata key.
            }
        }
        return null;
    }

    private static List<String> extractTitleOutlineSample(String markdown) {
        Matcher matcher = MARKDOWN_HEADING.matcher(markdown);
        List<String> headings = new java.util.ArrayList<>();
        while (matcher.find() && headings.size() < 3) {
            headings.add(matcher.group(1).trim());
        }
        return headings;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
