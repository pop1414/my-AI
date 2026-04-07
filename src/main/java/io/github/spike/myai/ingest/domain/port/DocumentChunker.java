package io.github.spike.myai.ingest.domain.port;

import java.util.List;

/**
 * 文档分块端口。
 */
public interface DocumentChunker {

    /**
     * 将纯文本拆分为可向量化的分块。
     *
     * @param text 纯文本
     * @return 分块文本列表
     */
    List<String> chunk(String text);
}

