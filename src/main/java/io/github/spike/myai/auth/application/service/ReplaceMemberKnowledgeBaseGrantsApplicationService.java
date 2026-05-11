package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.ReplaceMemberKnowledgeBaseGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberKnowledgeBaseGrantsUseCase;
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
 * 以成员为维度批量覆盖知识库授权应用服务。
 *
 * <p>实现 {@link ReplaceMemberKnowledgeBaseGrantsUseCase} 用例，
 * 将指定成员的知识库授权集合整体替换为传入的期望授权列表。
 *
 * <h3>替换语义（声明式同步）</h3>
 * <ol>
 *   <li><strong>新增：</strong>期望授权中不存在于现有授权的 kbId → 新建 grant</li>
 *   <li><strong>更新：</strong>期望授权与现有授权 kbId 相同但角色不同 → 覆写 grant</li>
 *   <li><strong>禁用：</strong>现有授权中的 kbId 不在期望授权中 → 软删除 grant</li>
 *   <li><strong>不变：</strong>期望授权与现有授权 kbid 和角色均相同 → 跳过</li>
 * </ol>
 * 整个过程在同一数据库事务中完成。
 */
@Service
public class ReplaceMemberKnowledgeBaseGrantsApplicationService implements ReplaceMemberKnowledgeBaseGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseGrantManagementRepository grantRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param workspaceMemberRepository   工作区成员仓储
     * @param knowledgeBaseRepository     知识库仓储（校验知识库存在性）
     * @param grantRepository             知识库授权管理仓储
     * @param auditEventRepository        审计事件仓储
     */
    public ReplaceMemberKnowledgeBaseGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            WorkspaceMemberRepository workspaceMemberRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行成员知识库授权批量替换。
     *
     * @param command 替换命令，包含目标成员 ID 和期望授权列表
     * @return 替换后的完整授权列表
     */
    @Override
    @Transactional
    public List<KnowledgeBaseGrantResult> handle(ReplaceMemberKnowledgeBaseGrantsCommand command) {
        // 1. 权限校验 + 目标成员定位
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + command.normalizedUserId()));
        // 治理边界：确保操作者有权管理目标成员角色的授权
        workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());

        // 2. 构建期望授权映射（kbId → role），同时校验知识库存在性
        Map<String, KnowledgeBaseRole> desiredAssignments = new LinkedHashMap<>();
        for (ReplaceMemberKnowledgeBaseGrantsCommand.Assignment assignment : command.assignments()) {
            if (knowledgeBaseRepository.findByKbId(currentUser.workspaceId(), assignment.normalizedKbId()).isEmpty()) {
                throw new IllegalArgumentException("knowledge base not found: " + assignment.normalizedKbId());
            }
            desiredAssignments.put(assignment.normalizedKbId(), assignment.resolvedRole());
        }

        // 3. 查询现有授权，建立 kbId → grant 索引
        List<KnowledgeBaseGrant> existingGrants = grantRepository.findActiveGrantsByUser(
                currentUser.workspaceId(),
                member.userId());
        Map<String, KnowledgeBaseGrant> existingByKbId = new LinkedHashMap<>();
        existingGrants.forEach(grant -> existingByKbId.put(grant.kbId(), grant));

        // 4. 对比差异并执行变更（新增 / 更新 / 禁用 / 不变）
        int createdCount = 0;
        int updatedCount = 0;
        int disabledCount = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, KnowledgeBaseRole> entry : desiredAssignments.entrySet()) {
            KnowledgeBaseGrant existing = existingByKbId.get(entry.getKey());
            if (existing == null) {
                // kbId 不在现有授权中 → 新增
                createdCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), entry.getKey(), member.userId(), entry.getValue(), now);
                continue;
            }
            if (existing.role() != entry.getValue()) {
                // kbId 相同但角色不同 → 更新
                updatedCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), entry.getKey(), member.userId(), entry.getValue(), now);
            }
            // kbId 和角色均相同 → 跳过
        }
        for (KnowledgeBaseGrant existing : existingGrants) {
            // 现有授权中的 kbId 不在期望授权中 → 禁用
            if (!desiredAssignments.containsKey(existing.kbId())
                    && grantRepository.disableGrant(currentUser.workspaceId(), existing.kbId(), member.userId(), now)) {
                disabledCount++;
            }
        }

        // 5. 审计记录（含创建/更新/禁用计数）
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MEMBER_KNOWLEDGE_BASE_GRANTS_REPLACED",
                "WORKSPACE_MEMBER",
                member.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s,"createdCount":%s,"updatedCount":%s,"disabledCount":%s}
                """.formatted(
                        toJsonString(member.userId()),
                        toJsonString(member.username()),
                        String.valueOf(createdCount),
                        String.valueOf(updatedCount),
                        String.valueOf(disabledCount)),
                now));

        // 6. 回查最新授权列表并返回
        return grantRepository.findActiveGrantsByUser(currentUser.workspaceId(), member.userId()).stream()
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
