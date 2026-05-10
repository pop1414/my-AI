package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ListKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询知识库授权列表应用服务。
 * <p>
 * 实现 {@link ListKnowledgeBaseGrantsUseCase} 用例，负责以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验并规范化 {@code kbId} 参数</li>
 *   <li>验证目标知识库在当前工作区中存在</li>
 *   <li>查询该知识库下所有活跃授权记录并转换为结果对象</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class ListKnowledgeBaseGrantsApplicationService implements ListKnowledgeBaseGrantsUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 知识库仓储，用于校验知识库存在性 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    /** 知识库授权治理仓储 */
    private final KnowledgeBaseGrantManagementRepository grantRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService    授权服务
     * @param knowledgeBaseRepository 知识库仓储
     * @param grantRepository         授权治理仓储
     */
    public ListKnowledgeBaseGrantsApplicationService(
            AuthorizationService authorizationService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseGrantManagementRepository grantRepository) {
        this.authorizationService = authorizationService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.grantRepository = grantRepository;
    }

    /**
     * 执行查询知识库授权列表用例。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验当前用户的工作区管理权限</li>
     *   <li>校验并规范化 {@code kbId}</li>
     *   <li>确认知识库存在，不存在则抛出 {@link ManagedKnowledgeBaseNotFoundException}</li>
     *   <li>查询活跃授权记录并转换为结果列表</li>
     * </ol>
     *
     * @param kbId 知识库唯一标识
     * @return 授权结果列表，无授权时返回空列表
     * @throws ManagedKnowledgeBaseNotFoundException 当知识库不存在时抛出
     * @throws IllegalArgumentException             当 {@code kbId} 为空或纯空白时抛出
     */
    @Override
    public List<KnowledgeBaseGrantResult> handle(String kbId) {
        // Step 1: 校验工作区管理权限，获取当前用户上下文
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // Step 2: 校验并规范化 kbId（非空、trim）
        String normalizedKbId = requireKbId(kbId);
        // Step 3: 确认知识库在当前工作区中存在
        ensureKnowledgeBaseExists(currentUser.workspaceId(), normalizedKbId);
        // Step 4: 查询活跃授权记录并转换为结果列表
        return grantRepository.findActiveGrants(currentUser.workspaceId(), normalizedKbId).stream()
                .map(grant -> new KnowledgeBaseGrantResult(
                        grant.workspaceId(),
                        grant.kbId(),
                        grant.userId(),
                        grant.username(),
                        grant.displayName(),
                        grant.role(),
                        grant.status()))
                .toList();
    }

    /**
     * 校验知识库在当前工作区中是否存在。
     * <p>
     * 若查询结果为空则抛出 {@link ManagedKnowledgeBaseNotFoundException}，
     * 由上层控制器映射为 HTTP 404。
     *
     * @param workspaceId 工作区 ID
     * @param kbId        知识库 ID
     * @throws ManagedKnowledgeBaseNotFoundException 当知识库不存在时抛出
     */
    private void ensureKnowledgeBaseExists(String workspaceId, String kbId) {
        if (knowledgeBaseRepository.findByKbId(workspaceId, kbId).isEmpty()) {
            throw new ManagedKnowledgeBaseNotFoundException("knowledge base not found: " + kbId);
        }
    }

    /**
     * 校验并规范化知识库 ID。
     *
     * @param kbId 原始知识库 ID
     * @return trim 后的知识库 ID
     * @throws IllegalArgumentException 当值为 {@code null} 或纯空白时抛出
     */
    private static String requireKbId(String kbId) {
        if (kbId == null || kbId.trim().isEmpty()) {
            throw new IllegalArgumentException("kbId is required");
        }
        return kbId.trim();
    }
}
