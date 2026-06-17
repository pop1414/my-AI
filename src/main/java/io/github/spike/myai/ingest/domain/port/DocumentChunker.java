package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import java.util.List;

/**
 * 文档分块端口。
 *
 * <p>定义纯文本 → 分块列表的契约。实现类负责将 Markdown 文本拆分为
 * 可向量化的 {@link DocumentChunk}，每条分块携带正文内容和结构化元数据。
 *
 * @author spike
 * @since 1.0.0
 */
public interface DocumentChunker {

    /**
     * 将纯文本拆分为可向量化的分块。
     *
     * @param text 纯文本（通常是清洗后的 Markdown）
     * @return 分块结果列表（含 chunkMetadata 结构化元数据）
     */
    List<DocumentChunk> chunk(String text);
}
