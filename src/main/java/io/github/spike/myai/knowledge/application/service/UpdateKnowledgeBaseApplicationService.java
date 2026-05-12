package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
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

    /** 当前用户上下文提供器，用于获取工作区标识 */
    private final CurrentUserProvider currentUserProvider;

    /** 应用层授权服务，用于校验知识库管理权限 */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * @param knowledgeBaseRepository 知识库持久化仓库
     * @param currentUserProvider     当前用户上下文提供器
     * @param authorizationService    授权服务
     */
    public UpdateKnowledgeBaseApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
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
        // ---------- 第零步：权限校验 ----------
        // 更新知识库前先校验管理权限：OWNER / ADMIN 或 KB_MANAGER 可执行
        // 注意：这里校验的是知识库级权限（requireCanManageKnowledgeBase），
        // 而非工作区级权限（requireCanManageWorkspace），允许被授权的 KB_MANAGER 编辑知识库
        authorizationService.requireCanManageKnowledgeBase(command.normalizedKbId());

        // ---------- 第一步：查找现有知识库 ----------
        // 获取当前登录用户的工作区，确保知识库查询限定在本工作区范围内
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String workspaceId = currentUser.workspaceId();
        // 按 ID 查找现有知识库，不存在则抛出业务异常
        // 使用 Optional.orElseThrow 实现快速失败（Fail-Fast），避免后续空指针
        KnowledgeBase current = knowledgeBaseRepository.findByKbId(workspaceId, command.normalizedKbId())
                .orElseThrow(() -> new KnowledgeBaseNotFoundException(
                        "knowledge base not found: " + command.normalizedKbId()));

        // ---------- 第二步：调用领域模型更新方法 ----------
        // normalizedXxxOrDefault 系列方法实现"传了则改、不传则保持原值"的语义：
        //   - 若命令中 name 非空 → 使用新名称（已 trim）
        //   - 若命令中 name 为空 → 使用 current.name()（保持原值）
        // 传入当前时间戳作为更新时间
        KnowledgeBase updated = current.update(
                command.normalizedNameOrDefault(current.name()),
                command.normalizedDescriptionOrDefault(current.description()),
                command.resolvedStatusOrDefault(current.status()),
                Instant.now());

        // ---------- 第三步：持久化更新后的聚合根 ----------
        // 仓储实现应保证原子性，通常使用 UPDATE WHERE 或乐观锁版本号
        knowledgeBaseRepository.save(updated);

        // ---------- 第四步：查询已索引文档数量 ----------
        // 从仓库的全量列表中过滤出当前知识库并提取索引计数
        // 使用 mapToLong 避免 Long 自动装箱，减少 GC 压力
        long indexedDocumentCount = knowledgeBaseRepository.listKnowledgeBases(workspaceId).stream()
                .filter(item -> item.kbId().equals(updated.kbId()))
                .mapToLong(item -> item.indexedDocumentCount())
                .findFirst()
                .orElse(0L);    // 未找到统计信息时默认为 0

        // ---------- 第五步：构建并返回应用层结果 ----------
        return new KnowledgeBaseResult(
                updated.kbId(),
                updated.name(),
                updated.description(),
                updated.status().name(),
                indexedDocumentCount);
    }
}
