package io.github.spike.myai.ingest.infrastructure.chunking;

import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 结构优先 + 长度兜底的确定性分块器。
 */
@Component
public class StructuredFallbackDocumentChunker implements DocumentChunker {

    private final int chunkSize;
    private final int overlapSize;

    public StructuredFallbackDocumentChunker(IngestProperties ingestProperties) {
        this.chunkSize = ingestProperties.getChunk().getChunkSize();
        this.overlapSize = ingestProperties.getChunk().getOverlapSize();
    }

    @Override
    public List<String> chunk(String text) {
        // 先归一化换行，保证相同输入在不同平台下分块结果稳定。
        String normalized = normalize(text);
        // 结构优先：先按“段落空行”切成语义片段，再做长度兜底。
        List<String> segments = splitByStructure(normalized);
        List<String> chunks = new ArrayList<>();
        List<String> currentTokens = new ArrayList<>();

        for (String segment : segments) {
            List<String> segmentTokens = tokenize(segment);
            if (segmentTokens.isEmpty()) {
                continue;
            }

            // 单段过长时，不与其它段混排，直接走窗口切分，保证边界可预测。
            if (segmentTokens.size() > chunkSize) {
                if (!currentTokens.isEmpty()) {
                    flushChunk(chunks, currentTokens);
                }
                chunkLongSegment(chunks, segmentTokens);
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
                flushChunk(chunks, currentTokens);
                currentTokens.addAll(segmentTokens);
            }
        }

        if (!currentTokens.isEmpty()) {
            chunks.add(renderTokens(currentTokens));
        }
        return chunks;
    }

    private void chunkLongSegment(List<String> chunks, List<String> segmentTokens) {
        // 滑动窗口步长 = chunkSize - overlapSize，确保相邻 chunk 有语义重叠。
        int step = Math.max(1, chunkSize - overlapSize);
        for (int start = 0; start < segmentTokens.size(); start += step) {
            int end = Math.min(segmentTokens.size(), start + chunkSize);
            List<String> window = segmentTokens.subList(start, end);
            chunks.add(renderTokens(window));
            if (end == segmentTokens.size()) {
                break;
            }
        }
    }

    private void flushChunk(List<String> chunks, List<String> currentTokens) {
        String chunk = renderTokens(currentTokens);
        chunks.add(chunk);

        // 刷出后保留尾部 overlap tokens，作为下一个 chunk 的前缀上下文。
        List<String> overlapTokens = takeTail(currentTokens, overlapSize);
        currentTokens.clear();
        currentTokens.addAll(overlapTokens);
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
}
