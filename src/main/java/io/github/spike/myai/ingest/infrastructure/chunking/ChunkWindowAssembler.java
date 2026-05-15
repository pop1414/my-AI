package io.github.spike.myai.ingest.infrastructure.chunking;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.SourceHint;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 按 chunk size 和 overlap 将结构片段组装为最终 chunk。
 */
final class ChunkWindowAssembler {

    private final int chunkSize;
    private final int overlapSize;

    ChunkWindowAssembler(int chunkSize, int overlapSize) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    List<DocumentChunk> assemble(List<HeadingContextExtractor.HeadingSegment> segments) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> currentTokens = new ArrayList<>();
        String currentHeading = null;
        String currentChunkHeading = null;

        for (HeadingContextExtractor.HeadingSegment headingSegment : segments) {
            String heading = headingSegment.heading();
            MarkdownSegmenter.Segment segment = headingSegment.segment();
            if (heading != null) {
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                currentHeading = heading;
                currentChunkHeading = currentHeading;
            } else if (currentTokens.isEmpty()) {
                currentChunkHeading = currentHeading;
            }

            List<String> segmentTokens = segment.tokens();
            if (segmentTokens.isEmpty()) {
                continue;
            }

            if (segmentTokens.size() > chunkSize) {
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                chunkLongSegment(chunks, segmentTokens, currentHeading);
                continue;
            }

            if (currentTokens.size() + segmentTokens.size() <= chunkSize) {
                if (!currentTokens.isEmpty()) {
                    currentTokens.add("\n");
                }
                currentTokens.addAll(segmentTokens);
            } else {
                currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                currentTokens.addAll(segmentTokens);
                currentChunkHeading = currentHeading;
            }
        }

        if (!currentTokens.isEmpty()) {
            chunks.add(new DocumentChunk(renderTokens(currentTokens), SourceHint.heading(currentChunkHeading)));
        }
        return chunks;
    }

    private void chunkLongSegment(List<DocumentChunk> chunks, List<String> segmentTokens, String heading) {
        int step = Math.max(1, chunkSize - overlapSize);
        for (int start = 0; start < segmentTokens.size(); start += step) {
            int end = Math.min(segmentTokens.size(), start + chunkSize);
            List<String> window = segmentTokens.subList(start, end);
            chunks.add(new DocumentChunk(renderTokens(window), SourceHint.heading(heading)));
            if (end == segmentTokens.size()) {
                break;
            }
        }
    }

    private String flushChunk(List<DocumentChunk> chunks, List<String> currentTokens, String currentHeading) {
        chunks.add(new DocumentChunk(renderTokens(currentTokens), SourceHint.heading(currentHeading)));
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

    private static String renderTokens(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !token.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }
}
