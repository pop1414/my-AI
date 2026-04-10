package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import java.util.List;

/**
 * 文档分块端口。
 */
public interface DocumentChunker {

    /**
     * 将纯文本拆分为可向量化的分块。
     *
     * @param text 纯文本
     * @return 分块结果列表（含可选的 sourceHint 信息）
     */
    List<DocumentChunk> chunk(String text);
}
