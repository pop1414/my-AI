package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.knowledge.application.command.UpdateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.UpdateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 编辑（更新）知识库应用服务。
 *
 * <p>该服务实现 {@link UpdateKnowledgeBaseUseCase} 用例接口，
 * 负责编排更新知识库的完整业务流程：
 * <ol>
 *   <li>根据命令中的 ID 查找现有知识库，不存在则抛出异常；</li>
 *   <li>调用领域模型的 {@code update} 方法生成更新后的聚合根
 *       （保留未传入字段的原有值）；</li>
 *   <li>持久化更新后的聚合根；</li>
 *   <li>查询该知识库当前的已索引文档数量；</li>
 *   <li>将领域对象映射为应用层结果返回。</li>
 * </ol>
 *
 * <p>与创建服务的区别：更新服务需要先查找现有实体，
 * 且字段更新采用"传了则改、不传则保持"的语义，
 * 具体由 {@link UpdateKnowledgeBaseCommand} 的 {@code normalizedXxxOrDefault}
 * 系列方法和领域模型的 {@code update} 方法协作完成。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class UpdateKnowledgeBaseApplicationService implements UpdateKnowledgeBaseUseCase {

    /** 知识库持久化仓库（领域端口），用于读写知识库聚合根 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 构造器注入。
     *
     * @param knowledgeBaseRepository 知识库持久化仓库
     */
    public UpdateKnowledgeBaseApplicationService(KnowledgeBaseRepository knowledgeBaseRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 处理更新知识库用例。
     *
     * <p>执行流程：
     * <ol>
     *   <li>按 ID 查找现有知识库，若不存在则抛出 {@link KnowledgeBaseNotFoundException}；</li>
     *   <li>调用领域模型的 {@code update} 方法，传入规整化后的新值或当前值作为默认值；</li>
     *   <li>持久化更新后的聚合根；</li>
     *   <li>从仓库列表中获取最新的已索引文档数量；</li>
     *   <li>构建并返回应用层结果对象。</li>
     * </ol>
     *
     * @param command 更新知识库命令（已通过规整化处理）
     * @return 更新后的知识库结果视图
     * @throws KnowledgeBaseNotFoundException 当指定 ID 的知识库不存在时
     */
    @Override
    public KnowledgeBaseResult handle(UpdateKnowledgeBaseCommand command) {
        // 1. 按 ID 查找现有知识库，不存在则抛出业务异常
        //    使用 Optional.orElseThrow 实现快速失败（Fail-Fast）
        KnowledgeBase current = knowledgeBaseRepository.findByKbId(command.normalizedKbId())
                .orElseThrow(() -> new KnowledgeBaseNotFoundException(
                        "knowledge base not found: " + command.normalizedKbId()));

        // 2. 调用领域模型更新方法
        //    normalizedXxxOrDefault 系列方法实现"不传则保持原值"的语义
        //    传入当前时间戳作为更新时间
        KnowledgeBase updated = current.update(
                command.normalizedNameOrDefault(current.name()),
                command.normalizedDescriptionOrDefault(current.description()),
                command.resolvedStatusOrDefault(current.status()),
                Instant.now());

        // 3. 持久化更新后的聚合根
        knowledgeBaseRepository.save(updated);

        // 4. 查询已索引文档数量
        //    从仓库的全量列表中过滤出当前知识库并提取索引计数
        //    使用 mapToLong 避免自动装箱，提升性能
        long indexedDocumentCount = knowledgeBaseRepository.listKnowledgeBases().stream()
                .filter(item -> item.kbId().equals(updated.kbId()))
                .mapToLong(item -> item.indexedDocumentCount())
                .findFirst()
                .orElse(0L);    // 未找到统计信息时默认为 0

        // 5. 构建并返回应用层结果对象
        return new KnowledgeBaseResult(
                updated.kbId(),
                updated.name(),
                updated.description(),
                updated.status().name(),
                indexedDocumentCount);
    }
}
