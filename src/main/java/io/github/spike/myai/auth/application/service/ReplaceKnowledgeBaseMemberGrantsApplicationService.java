package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.ReplaceKnowledgeBaseMemberGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ReplaceKnowledgeBaseMemberGrantsUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以知识库为维度批量覆盖成员授权应用服务。
 *
 * <p>实现 {@link ReplaceKnowledgeBaseMemberGrantsUseCase} 用例，
 * 将指定知识库的成员授权集合整体替换为传入的期望授权列表。
 *
 * <h3>替换语义（声明式同步）</h3>
 * <ol>
 *   <li><strong>新增：</strong>期望授权中的 userId 不在现有授权中 → 新建 grant</li>
 *   <li><strong>更新：</strong>期望授权与现有授权 userId 相同但角色不同 → 覆写 grant</li>
 *   <li><strong>禁用：</strong>现有授权中的 userId 不在期望授权中 → 软删除 grant</li>
 *   <li><strong>不变：</strong>期望授权与现有授权 userId 和角色均相同 → 跳过</li>
 * </ol>
 * 整个过程在同一数据库事务中完成。
 */
@Service
public class ReplaceKnowledgeBaseMemberGrantsApplicationService implements ReplaceKnowledgeBaseMemberGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final KnowledgeBaseGrantManagementRepository grantRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param knowledgeBaseRepository     知识库仓储
     * @param workspaceMemberRepository   工作区成员仓储
     * @param grantRepository             知识库授权管理仓储
     * @param auditEventRepository        审计事件仓储
     */
    public ReplaceKnowledgeBaseMemberGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            KnowledgeBaseRepository knowledgeBaseRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            KnowledgeBaseGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行知识库成员授权批量替换。
     *
     * @param command 替换命令，包含目标知识库 ID 和期望授权列表
     * @return 替换后的完整授权列表
     */
    @Override
    @Transactional
    public List<KnowledgeBaseGrantResult> handle(ReplaceKnowledgeBaseMemberGrantsCommand command) {
        // 1. 权限校验 + 知识库存在性校验
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        String kbId = command.normalizedKbId();
        if (knowledgeBaseRepository.findByKbId(currentUser.workspaceId(), kbId).isEmpty()) {
            throw new ManagedKnowledgeBaseNotFoundException("knowledge base not found: " + kbId);
        }

        // 2. 构建期望授权映射（userId → role），同时校验成员存在性 + 治理边界
        Map<String, KnowledgeBaseRole> desiredAssignments = new LinkedHashMap<>();
        for (ReplaceKnowledgeBaseMemberGrantsCommand.Assignment assignment : command.assignments()) {
            WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                            currentUser.workspaceId(),
                            assignment.normalizedUserId())
                    .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + assignment.normalizedUserId()));
            // 治理边界：确保操作者有权管理目标成员角色的授权
            workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());
            desiredAssignments.put(member.userId(), assignment.resolvedRole());
        }

        // 3. 查询现有授权，建立 userId → grant 索引
        List<KnowledgeBaseGrant> existingGrants = grantRepository.findActiveGrants(currentUser.workspaceId(), kbId);
        Map<String, KnowledgeBaseGrant> existingByUserId = new LinkedHashMap<>();
        existingGrants.forEach(grant -> existingByUserId.put(grant.userId(), grant));

        // 4. 对比差异并执行变更（新增 / 更新 / 禁用 / 不变）
        int createdCount = 0;
        int updatedCount = 0;
        int disabledCount = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, KnowledgeBaseRole> entry : desiredAssignments.entrySet()) {
            KnowledgeBaseGrant existing = existingByUserId.get(entry.getKey());
            if (existing == null) {
                // userId 不在现有授权中 → 新增
                createdCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), kbId, entry.getKey(), entry.getValue(), now);
                continue;
            }
            if (existing.role() != entry.getValue()) {
                // userId 相同但角色不同 → 更新
                updatedCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), kbId, entry.getKey(), entry.getValue(), now);
            }
            // userId 和角色均相同 → 跳过
        }
        for (KnowledgeBaseGrant existing : existingGrants) {
            // 现有授权中的 userId 不在期望授权中 → 禁用
            if (!desiredAssignments.containsKey(existing.userId())
                    && grantRepository.disableGrant(currentUser.workspaceId(), kbId, existing.userId(), now)) {
                disabledCount++;
            }
        }

        // 5. 审计记录
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "KNOWLEDGE_BASE_MEMBER_GRANTS_REPLACED",
                "KNOWLEDGE_BASE",
                kbId,
                "SUCCESS",
                "",
                """
                {"kbId":%s,"createdCount":%s,"updatedCount":%s,"disabledCount":%s}
                """.formatted(
                        toJsonString(kbId),
                        String.valueOf(createdCount),
                        String.valueOf(updatedCount),
                        String.valueOf(disabledCount)),
                now));

        // 6. 回查最新授权列表并返回
        return grantRepository.findActiveGrants(currentUser.workspaceId(), kbId).stream()
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
     * 将字符串包装为 JSON 字符串值（转义特殊字符）。
     */
    private static String toJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
