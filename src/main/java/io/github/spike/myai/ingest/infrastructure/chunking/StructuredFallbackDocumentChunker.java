package io.github.spike.myai.ingest.infrastructure.chunking;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.List;
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

    private final MarkdownSegmenter segmenter = new MarkdownSegmenter();
    private final HeadingContextExtractor headingContextExtractor = new HeadingContextExtractor();
    private final ChunkWindowAssembler windowAssembler;

    public StructuredFallbackDocumentChunker(IngestProperties ingestProperties) {
        this.windowAssembler = new ChunkWindowAssembler(
                ingestProperties.getChunk().getChunkSize(),
                ingestProperties.getChunk().getOverlapSize());
    }

    @Override
    public List<DocumentChunk> chunk(String text) {
        List<MarkdownSegmenter.Segment> segments = segmenter.segment(text);
        List<HeadingContextExtractor.HeadingSegment> headingSegments = headingContextExtractor.extract(segments);
        return windowAssembler.assemble(headingSegments);
    }
}
