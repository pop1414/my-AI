package io.github.spike.myai.qa.infrastructure.reranking;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.RerankingPort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 透传重排序适配器（No-Op 实现）。
 *
 * <p>不做任何重排序或评分调整，直接返回输入列表的前 topN 条。
 * 该实现作为 {@link RerankingPort} 的默认注入，确保现有问答流程行为不变。
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class NoOpRerankingAdapter implements RerankingPort {

    @Override
    public List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (topN >= candidates.size()) {
            return List.copyOf(candidates);
        }
        return List.copyOf(candidates.subList(0, topN));
    }
}
