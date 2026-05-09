package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.knowledge.application.command.CreateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.CreateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseIdGenerator;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
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

    /** 应用层授权服务，用于校验当前用户是否具备工作区管理权限（OWNER / ADMIN） */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * <p>所有依赖均为领域端口或应用层服务，不依赖基础设施层实现，
     * 符合六边形架构的依赖方向（外层依赖内层）。
     *
     * @param knowledgeBaseIdGenerator 知识库 ID 生成器（领域端口）
     * @param knowledgeBaseRepository  知识库持久化仓库（领域端口）
     * @param authorizationService     授权服务（应用层）
     */
    public CreateKnowledgeBaseApplicationService(
            KnowledgeBaseIdGenerator knowledgeBaseIdGenerator,
            KnowledgeBaseRepository knowledgeBaseRepository,
            AuthorizationService authorizationService) {
        this.knowledgeBaseIdGenerator = knowledgeBaseIdGenerator;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.authorizationService = authorizationService;
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
        // ---------- 第零步：权限校验 ----------
        // 创建知识库属于工作区级管理操作，仅 OWNER / ADMIN 可执行
        // 权限不足时直接抛出 AccessDeniedException，不创建任何资源
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // ---------- 第一步：准备创建参数 ----------
        // 获取当前时间戳，作为知识库的创建时间和更新时间
        Instant now = Instant.now();

        // ---------- 第二步：通过领域模型工厂方法创建聚合根 ----------
        // KnowledgeBase.create() 内部执行不变性校验（名称非空、ID 格式有效等），
        // 校验失败时在领域层即抛出异常，避免无效聚合根进入存储层
        KnowledgeBase knowledgeBase = KnowledgeBase.create(
                knowledgeBaseIdGenerator.nextKbId(),    // 生成全局唯一 ID（如 kb_xxx 格式）
                currentUser.workspaceId(),               // 使用当前登录用户所属工作区
                command.normalizedName(),                // 规整化后的名称（已 trim + 长度限制）
                command.normalizedDescription(),         // 规整化后的描述（已 trim + 长度限制）
                command.resolvedStatus(),                // 解析后的状态（含默认值处理）
                now);                                    // 创建时间

        // ---------- 第三步：持久化 ----------
        // 将聚合根写入存储层，由仓储实现负责具体的 INSERT 或 UPSERT 语义
        knowledgeBaseRepository.save(knowledgeBase);

        // ---------- 第四步：构建返回结果 ----------
        // 将领域对象映射为应用层 DTO，隔离领域模型对接口层的暴露
        // 新建知识库的已索引文档数固定为 0
        return new KnowledgeBaseResult(
                knowledgeBase.kbId(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.status().name(),   // 枚举转字符串，确保接口契约稳定
                0L);                             // 新建知识库尚无索引文档
    }
}
