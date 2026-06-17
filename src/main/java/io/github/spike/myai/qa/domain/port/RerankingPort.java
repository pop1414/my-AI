package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import java.util.List;

/**
 * 重排序端口（Domain Port）。
 *
 * <p>定义问答流程中检索结果的重排序能力，允许在检索后、上下文拼接前
 * 对候选分块进行二次排序或截断，而不侵入检索实现。
 *
 * <p>默认实现为透传（NoOp），预留扩展点供未来引入
 * 基于模型的 Reranking 或自定义排序策略。
 *
 * @author spike
 * @since 1.0.0
 */
public interface RerankingPort {

    /**
     * 对检索候选结果进行重排序。
     *
     * @param candidates 检索返回的候选分块列表
     * @param question 用户原始问题
     * @param topN 最终保留的分块数量
     * @return 重排序后的分块列表，长度不超过 topN
     */
    List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN);
}
