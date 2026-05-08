package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询知识库列表应用服务。
 *
 * <p>该服务实现 {@link ListKnowledgeBasesUseCase} 用例接口，负责：
 * <ul>
 *   <li>调用领域端口 {@link KnowledgeBaseRepository#listKnowledgeBases()}
 *       读取知识库主数据与聚合统计；</li>
 *   <li>将领域模型流式映射为应用层 {@link KnowledgeBaseResult} 结果对象；</li>
 *   <li>封装当前版本（V1.1）的业务约定与输出口径。</li>
 * </ul>
 *
 * <p>设计说明：该服务为只读操作，不涉及事务管理。
 * 映射过程中将领域枚举 {@code status} 转为字符串，
 * 确保接口契约与领域模型解耦。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ListKnowledgeBasesApplicationService implements ListKnowledgeBasesUseCase {

    /** 知识库持久化仓库（领域端口），用于读取知识库列表及聚合数据 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 构造器注入。
     *
     * @param knowledgeBaseRepository 知识库持久化仓库
     */
    public ListKnowledgeBasesApplicationService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 处理查询知识库列表用例（无参，返回全量列表）。
     *
     * <p>执行流程：
     * <ol>
     *   <li>从仓库获取知识库全量列表（已包含聚合统计字段）；</li>
     *   <li>通过 Stream 流式处理，将每个领域视图项映射为应用层结果对象；</li>
     *   <li>映射时将 {@code status} 枚举转为字符串，确保接口稳定性；</li>
     *   <li>收集为不可变列表并返回。</li>
     * </ol>
     *
     * @return 知识库结果列表（可能为空列表，不会返回 {@code null}）
     */
    @Override
    public List<KnowledgeBaseResult> handle() {
        // 从仓库获取全量知识库视图列表，通过 Stream 流式转换为应用层 DTO
        // listKnowledgeBases() 返回的视图项已包含 indexedDocumentCount 聚合字段
        return knowledgeBaseRepository.listKnowledgeBases().stream()
                .map(item -> new KnowledgeBaseResult(
                        item.kbId(),                     // 知识库唯一标识
                        item.name(),                     // 知识库名称
                        item.description(),              // 知识库描述
                        item.status().name(),            // 枚举转字符串，解耦接口契约
                        item.indexedDocumentCount()))    // 已索引文档数（聚合统计）
                .toList();  // 收集为不可变列表（Java 16+）
    }
}
