package io.github.spike.myai.ingest.infrastructure.chunking;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 结构优先 + 长度兜底的确定性分块器。
 *
 * <p>新增能力：
 * <ul>
 *   <li>提取标题上下文，写入 sourceHint（JSON 字符串）。</li>
 *   <li>支持 Markdown 标题与常见中文标题模式。</li>
 * </ul>
 */
@Component
public class StructuredFallbackDocumentChunker implements DocumentChunker {

    private final int chunkSize;
    private final int overlapSize;
    // Markdown 标题：# / ## / ###
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    // 中文标题：第X章/节/篇
    private static final Pattern CHINESE_HEADING = Pattern.compile("^(第[\\p{IsHan}0-9]+[章节篇])\\s*(.*)$");
    // 数字标题：1. / 1.1 / 1.1.1
    private static final Pattern NUMBER_HEADING = Pattern.compile("^\\d+(?:\\.\\d+)*\\s+.+$");

    public StructuredFallbackDocumentChunker(IngestProperties ingestProperties) {
        this.chunkSize = ingestProperties.getChunk().getChunkSize();
        this.overlapSize = ingestProperties.getChunk().getOverlapSize();
    }

    @Override
    public List<DocumentChunk> chunk(String text) {
        // 先归一化换行，保证相同输入在不同平台下分块结果稳定。
        String normalized = normalize(text);
        // 结构优先：先按“段落空行”切成语义片段，再做长度兜底。
        List<String> segments = splitByStructure(normalized);
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> currentTokens = new ArrayList<>();
        // 记录“当前标题上下文”，用于写入 sourceHint。
        String currentHeading = null;
        String currentChunkHeading = null;

        for (String segment : segments) {
            String heading = extractHeading(segment);
            if (heading != null) {
                // 标题出现时，优先刷出当前 chunk，保证标题边界稳定。
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                currentHeading = heading;
                currentChunkHeading = currentHeading;
            } else if (currentTokens.isEmpty()) {
                // 无标题段落，沿用上一段标题上下文。
                currentChunkHeading = currentHeading;
            }
            List<String> segmentTokens = tokenize(segment);
            if (segmentTokens.isEmpty()) {
                continue;
            }

            // 单段过长时，不与其它段混排，直接走窗口切分，保证边界可预测。
            if (segmentTokens.size() > chunkSize) {
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                // 过长段落单独切分，避免跨段混排。
                chunkLongSegment(chunks, segmentTokens, currentHeading);
                continue;
            }

            // 当前 chunk 还能容纳时继续拼接，尽量保留段落完整性。
            if (currentTokens.size() + segmentTokens.size() <= chunkSize) {
                if (!currentTokens.isEmpty()) {
                    // 这里人为加一个分隔 token，避免相邻段落无缝粘连。
                    currentTokens.add("\n");
                }
                currentTokens.addAll(segmentTokens);
            } else {
                // 放不下则先刷出当前 chunk，再以当前段作为新 chunk 起点。
                currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                currentTokens.addAll(segmentTokens);
                currentChunkHeading = currentHeading;
            }
        }

        if (!currentTokens.isEmpty()) {
            String content = renderTokens(currentTokens);
            // 末尾 chunk 同样写入 sourceHint。
            chunks.add(new DocumentChunk(content, toSourceHintJson(currentChunkHeading)));
        }
        return chunks;
    }

    private void chunkLongSegment(List<DocumentChunk> chunks, List<String> segmentTokens, String heading) {
        // 滑动窗口步长 = chunkSize - overlapSize，确保相邻 chunk 有语义重叠。
        int step = Math.max(1, chunkSize - overlapSize);
        for (int start = 0; start < segmentTokens.size(); start += step) {
            int end = Math.min(segmentTokens.size(), start + chunkSize);
            List<String> window = segmentTokens.subList(start, end);
            String content = renderTokens(window);
            // 长段切分时同样保留标题上下文。
            chunks.add(new DocumentChunk(content, toSourceHintJson(heading)));
            if (end == segmentTokens.size()) {
                break;
            }
        }
    }

    private String flushChunk(List<DocumentChunk> chunks, List<String> currentTokens, String currentHeading) {
        String chunk = renderTokens(currentTokens);
        // flush 时写入 sourceHint，保证 chunk 级别可追踪。
        chunks.add(new DocumentChunk(chunk, toSourceHintJson(currentHeading)));

        // 刷出后保留尾部 overlap tokens，作为下一个 chunk 的前缀上下文。
        List<String> overlapTokens = takeTail(currentTokens, overlapSize);
        currentTokens.clear();
        currentTokens.addAll(overlapTokens);
        return currentHeading;
    }

    private static List<String> takeTail(List<String> tokens, int tailSize) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, tokens.size() - tailSize);
        return new ArrayList<>(tokens.subList(from, tokens.size()));
    }

    private static String normalize(String text) {
        // 统一 CRLF/CR 为 LF，降低平台差异对分块边界的影响。
        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static List<String> splitByStructure(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    private static List<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String renderTokens(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !token.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    private static String extractHeading(String segment) {
        String firstLine = firstLine(segment);
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
        return null;
    }

    private static String firstLine(String segment) {
        String trimmed = segment.strip();
        int idx = trimmed.indexOf('\n');
        if (idx >= 0) {
            return trimmed.substring(0, idx).trim();
        }
        return trimmed;
    }

    private static String toSourceHintJson(String heading) {
        if (heading == null || heading.isBlank()) {
            return null;
        }
        // 统一用 JSON 字符串，方便前端做结构化展示。
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
