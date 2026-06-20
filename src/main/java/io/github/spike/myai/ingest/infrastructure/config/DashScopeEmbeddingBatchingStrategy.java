package io.github.spike.myai.ingest.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.util.Assert;

/**
 * DashScope Embedding 批处理策略。
 *
 * <p>在保留 Spring AI 默认 token 安全拆批能力的前提下，额外保证单次
 * DashScope Embedding 请求最多只携带 10 条文本，避免因批量条数超限触发
 * HTTP 400 参数错误。
 *
 * <p>处理顺序分为两层：
 * <ol>
 *   <li>先按照 DashScope 条数上限进行粗粒度切分；</li>
 *   <li>再将每个小批次委托给 {@link TokenCountBatchingStrategy} 做 token 维度兜底拆分。</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public class DashScopeEmbeddingBatchingStrategy implements BatchingStrategy {

    /** DashScope Embedding 单次请求允许的最大文本条数。 */
    static final int DEFAULT_MAX_DOCUMENTS_PER_BATCH = 10;

    /** Token 维度兜底批处理策略，负责避免单批次 token 总量过大。 */
    private final BatchingStrategy delegate;

    /** DashScope 单次请求最大文档条数限制。 */
    private final int maxDocumentsPerBatch;

    /**
     * 创建默认批处理策略。
     *
     * <p>默认使用 DashScope 10 条上限，并委托给 Spring AI 内置的
     * {@link TokenCountBatchingStrategy} 做 token 拆分。
     */
    public DashScopeEmbeddingBatchingStrategy() {
        this(DEFAULT_MAX_DOCUMENTS_PER_BATCH, new TokenCountBatchingStrategy());
    }

    /**
     * 创建可测试的批处理策略实例。
     *
     * @param maxDocumentsPerBatch DashScope 单次请求允许的最大文档条数
     * @param delegate token 维度兜底拆分策略
     */
    DashScopeEmbeddingBatchingStrategy(int maxDocumentsPerBatch, BatchingStrategy delegate) {
        Assert.isTrue(maxDocumentsPerBatch > 0, "maxDocumentsPerBatch must be greater than 0");
        Assert.notNull(delegate, "delegate must not be null");
        this.maxDocumentsPerBatch = maxDocumentsPerBatch;
        this.delegate = delegate;
    }

    /**
     * 将待嵌入文档拆分为满足 DashScope 条数限制的多个子批次。
     *
     * <p>返回的所有子批次会保持原始顺序，确保 embedding 结果和原始文档顺序一一对应。
     *
     * @param documents 待嵌入文档列表
     * @return 满足 DashScope 条数限制和 token 限制的子批次列表
     */
    @Override
    public List<List<Document>> batch(List<Document> documents) {
        Assert.notNull(documents, "documents must not be null");
        if (documents.isEmpty()) {
            return List.of();
        }

        List<List<Document>> batches = new ArrayList<>();
        for (int start = 0; start < documents.size(); start += this.maxDocumentsPerBatch) {
            int end = Math.min(start + this.maxDocumentsPerBatch, documents.size());
            List<Document> documentCountLimitedBatch = new ArrayList<>(documents.subList(start, end));
            batches.addAll(this.delegate.batch(documentCountLimitedBatch));
        }
        return batches;
    }
}
