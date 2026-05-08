package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.knowledge.application.command.CreateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.CreateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseIdGenerator;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 创建知识库应用服务。
 *
 * <p>该服务实现 {@link CreateKnowledgeBaseUseCase} 用例接口，
 * 负责编排创建知识库的完整业务流程：
 * <ol>
 *   <li>通过 {@link KnowledgeBaseIdGenerator} 生成全局唯一标识；</li>
 *   <li>调用领域模型 {@link KnowledgeBase#create} 工厂方法构造聚合根；</li>
 *   <li>通过 {@link KnowledgeBaseRepository} 持久化到存储层；</li>
 *   <li>将领域对象映射为应用层 {@link KnowledgeBaseResult} 返回给调用方。</li>
 * </ol>
 *
 * <p>设计原则：应用服务不包含业务规则，所有业务判断下沉到领域模型
 * （如 {@code KnowledgeBase.create} 内的不变性校验），
 * 服务层仅负责流程编排与端口调用，符合六边形架构的端口-适配器模式。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class CreateKnowledgeBaseApplicationService implements CreateKnowledgeBaseUseCase {

    /** 知识库 ID 生成器（领域端口），用于生成全局唯一的知识库标识 */
    private final KnowledgeBaseIdGenerator knowledgeBaseIdGenerator;

    /** 知识库持久化仓库（领域端口），用于保存知识库聚合根 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     *
     * @param knowledgeBaseIdGenerator 知识库 ID 生成器
     * @param knowledgeBaseRepository 知识库持久化仓库
     */
    public CreateKnowledgeBaseApplicationService(
            KnowledgeBaseIdGenerator knowledgeBaseIdGenerator,
            KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseIdGenerator = knowledgeBaseIdGenerator;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 处理创建知识库用例。
     *
     * <p>执行流程：
     * <ol>
     *   <li>获取当前时间戳作为创建时间；</li>
     *   <li>通过 ID 生成器生成新的知识库 ID；</li>
     *   <li>从命令对象中提取已规整化的名称、描述及状态；</li>
     *   <li>调用领域模型的工厂方法创建聚合根；</li>
     *   <li>将聚合根持久化到仓库；</li>
     *   <li>构建应用层结果对象并返回。</li>
     * </ol>
     *
     * @param command 创建知识库命令（已通过规整化处理）
     * @return 创建后的知识库结果视图
     */
    @Override
    public KnowledgeBaseResult handle(CreateKnowledgeBaseCommand command) {
        // 获取当前时间戳，作为知识库的创建时间
        Instant now = Instant.now();

        // 调用领域模型工厂方法创建知识库聚合根
        // 参数均来自 Command 对象的规整化方法，确保数据一致性
        KnowledgeBase knowledgeBase = KnowledgeBase.create(
                knowledgeBaseIdGenerator.nextKbId(),    // 生成全局唯一 ID
                WorkspaceConstants.DEFAULT_WORKSPACE_ID, // 当前阶段显式落入默认工作区
                command.normalizedName(),                // 规整化后的名称
                command.normalizedDescription(),         // 规整化后的描述
                command.resolvedStatus(),                // 解析后的状态（含默认值处理）
                now);                                    // 创建时间

        // 将新建的知识库聚合根持久化到存储层
        knowledgeBaseRepository.save(knowledgeBase);

        // 构建应用层结果对象并返回
        // 新建知识库的已索引文档数固定为 0
        return new KnowledgeBaseResult(
                knowledgeBase.kbId(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.status().name(),   // 枚举转字符串，确保接口契约稳定
                0L);                             // 新建知识库尚无索引文档
    }
}
