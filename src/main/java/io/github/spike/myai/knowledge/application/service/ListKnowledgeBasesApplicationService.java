package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseQueryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询知识库列表应用服务。
 *
 * <p>该服务实现应用层用例，负责：
 * <ul>
 *   <li>调用领域端口读取知识库聚合统计；</li>
 *   <li>将领域模型映射为应用层结果对象；</li>
 *   <li>封装当前版本（V1）的业务约定与输出口径。</li>
 * </ul>
 *
 * <p>V1 统计口径固定为 status = INDEXED 的文档数量。
 */
@Service
public class ListKnowledgeBasesApplicationService implements ListKnowledgeBasesUseCase {

    private final KnowledgeBaseQueryRepository knowledgeBaseQueryRepository;

    public ListKnowledgeBasesApplicationService(KnowledgeBaseQueryRepository knowledgeBaseQueryRepository) {
        this.knowledgeBaseQueryRepository = knowledgeBaseQueryRepository;
    }

    @Override
    public List<KnowledgeBaseResult> handle() {
        return knowledgeBaseQueryRepository.listIndexedKnowledgeBases().stream()
                // V1 尚未维护独立知识库主数据，因此 name 暂与 id 保持一致。
                .map(item -> new KnowledgeBaseResult(item.kbId(), item.kbId(), item.indexedDocumentCount()))
                .toList();
    }
}
