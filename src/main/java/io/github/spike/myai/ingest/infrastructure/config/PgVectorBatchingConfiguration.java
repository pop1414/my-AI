package io.github.spike.myai.ingest.infrastructure.config;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PGVector 向量批处理配置。
 *
 * <p>通过注册自定义 {@link BatchingStrategy} 覆盖 Spring AI PGVector 默认的
 * token-only 批处理策略，使文档向量化时同时满足：
 * <ul>
 *   <li>DashScope 单次 Embedding 请求最多 10 条文本；</li>
 *   <li>Spring AI 默认的 token 数量安全阈值。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Configuration
public class PgVectorBatchingConfiguration {

    /**
     * 注册 DashScope Embedding 专用批处理策略。
     *
     * @return 同时具备条数限制和 token 限制的批处理策略
     */
    @Bean
    public BatchingStrategy dashScopeEmbeddingBatchingStrategy() {
        return new DashScopeEmbeddingBatchingStrategy();
    }
}
