package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 查询知识库列表应用服务。
 *
 * <p>该服务实现 {@link ListKnowledgeBasesUseCase} 用例接口，负责：
 * <ul>
 *   <li>调用领域端口 {@link KnowledgeBaseRepository#listKnowledgeBases(String)}
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

    /** 当前用户上下文提供器，用于获取工作区标识 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权 grant 读取仓储，用于按知识库授权收紧普通成员的可见范围 */
    private final AuthorizationGrantRepository authorizationGrantRepository;

    /**
     * 构造器注入。
     *
     * @param knowledgeBaseRepository 知识库持久化仓库
     * @param currentUserProvider     当前用户上下文提供器
     */
    public ListKnowledgeBasesApplicationService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationGrantRepository authorizationGrantRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationGrantRepository = authorizationGrantRepository;
    }

    /**
     * 处理查询知识库列表用例。
     *
     * <p>执行流程：
     * <ol>
     *   <li>按当前用户角色与 includeDeleted 标记获取知识库列表（已包含聚合统计字段）；</li>
     *   <li>通过 Stream 流式处理，将每个领域视图项映射为应用层结果对象；</li>
     *   <li>映射时将 {@code status} 枚举转为字符串，确保接口稳定性；</li>
     *   <li>收集为不可变列表并返回。</li>
     * </ol>
     *
     * @param includeDeleted 是否包含已软删除知识库；仅工作区 Owner/Admin 生效
     * @return 知识库结果列表（可能为空列表，不会返回 {@code null}）
     */
    @Override
    public List<KnowledgeBaseResult> handle(boolean includeDeleted) {
        // 获取当前登录用户，确保已认证且可获取工作区标识
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        List<KnowledgeBaseSummary> visibleKnowledgeBases = resolveVisibleKnowledgeBases(currentUser, includeDeleted);

        // 从仓库获取全量知识库视图列表，通过 Stream 流式转换为应用层 DTO
        // listKnowledgeBases() 返回的视图项已包含 indexedDocumentCount 聚合字段，
        // 无需额外的数据库查询即可获得完整的列表数据
        return visibleKnowledgeBases.stream()
                .map(item -> new KnowledgeBaseResult(
                        item.kbId(),                     // 知识库唯一标识
                        item.name(),                     // 知识库名称
                        item.description(),              // 知识库描述
                        item.status().name(),            // 枚举转字符串，解耦接口契约
                        item.indexedDocumentCount()))    // 已索引文档数（聚合统计）
                .toList();  // 收集为不可变列表（Java 16+），替代 Collectors.toUnmodifiableList()
    }

    /**
     * 解析当前用户可见的知识库列表。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code WORKSPACE_OWNER / WORKSPACE_ADMIN} —— 可见当前工作区全部知识库，可按需包含软删除记录；</li>
     *   <li>{@code WORKSPACE_MEMBER} —— 仅可见自己具备 ACTIVE 显式知识库授权的知识库。</li>
     * </ul>
     *
     * @param currentUser    当前登录用户
     * @param includeDeleted 是否包含已软删除知识库；仅管理员视角生效
     * @return 当前用户可见的知识库摘要列表
     */
    private List<KnowledgeBaseSummary> resolveVisibleKnowledgeBases(CurrentUser currentUser, boolean includeDeleted) {
        boolean canAccessDeleted = currentUser.workspaceRole() == WorkspaceRole.WORKSPACE_OWNER
                || currentUser.workspaceRole() == WorkspaceRole.WORKSPACE_ADMIN;
        List<KnowledgeBaseSummary> allKnowledgeBases = includeDeleted && canAccessDeleted
                ? knowledgeBaseRepository.listKnowledgeBasesIncludingDeleted(currentUser.workspaceId())
                : knowledgeBaseRepository.listKnowledgeBases(currentUser.workspaceId());
        if (currentUser.workspaceRole() == WorkspaceRole.WORKSPACE_OWNER
                || currentUser.workspaceRole() == WorkspaceRole.WORKSPACE_ADMIN) {
            return allKnowledgeBases;
        }

        Set<String> grantedKnowledgeBaseIds = authorizationGrantRepository.listGrantedKnowledgeBaseIds(
                currentUser.workspaceId(),
                currentUser.userId());
        if (grantedKnowledgeBaseIds.isEmpty()) {
            return List.of();
        }

        return allKnowledgeBases.stream()
                .filter(item -> grantedKnowledgeBaseIds.contains(item.kbId()))
                .toList();
    }
}
