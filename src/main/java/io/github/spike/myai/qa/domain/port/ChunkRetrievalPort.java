package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
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

    /**
     * 在指定可问答文档版本范围内执行语义检索。
     *
     * <p>调用方必须先完成授权和版本选择，再将 scope 传入检索端。
     * 检索实现应把该范围下推到底层向量查询，避免先召回无权内容再裁剪。</p>
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数
     * @param scope 可问答文档版本范围
     * @return 命中的分块列表，顺序由相似度排序策略决定
     */
    List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope);
}
