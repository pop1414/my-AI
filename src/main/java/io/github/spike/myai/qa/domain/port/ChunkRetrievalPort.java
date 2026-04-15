package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import java.util.List;

/**
 * 问答检索端口（Domain Port）。
 *
 * <p>定义问答流程所需的“相似分块召回”能力。
 * 应用层仅依赖此抽象，不感知底层是 PGVector、Elasticsearch 还是其他检索引擎。
 */
public interface ChunkRetrievalPort {

    /**
     * 执行语义检索。
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数（实现方可做最小值保护）
     * @return 命中的分块列表，顺序由实现方的相似度排序策略决定
     */
    List<RetrievedChunk> similaritySearch(String question, int topK);
}
