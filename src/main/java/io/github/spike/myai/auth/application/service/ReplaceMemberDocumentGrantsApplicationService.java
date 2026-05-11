package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.ReplaceMemberDocumentGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以成员为维度批量覆盖文档授权应用服务。
 *
 * <p>实现 {@link ReplaceMemberDocumentGrantsUseCase} 用例，
 * 将指定成员的文档授权集合整体替换为传入的期望授权列表。
 *
 * <h3>替换语义（声明式同步）</h3>
 * <ol>
 *   <li><strong>新增：</strong>期望授权中的 documentId 不在现有授权中 → 新建 grant</li>
 *   <li><strong>更新：</strong>期望授权与现有授权 documentId 相同但权限不同 → 覆写 grant</li>
 *   <li><strong>禁用：</strong>现有授权中的 documentId 不在期望授权中 → 软删除 grant</li>
 *   <li><strong>不变：</strong>期望授权与现有授权 documentId 和权限均相同 → 跳过</li>
 * </ol>
 * 整个过程在同一数据库事务中完成。
 */
@Service
public class ReplaceMemberDocumentGrantsApplicationService implements ReplaceMemberDocumentGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DocumentRepository documentRepository;
    private final DocumentGrantManagementRepository grantRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param workspaceMemberRepository   工作区成员仓储
     * @param documentRepository          文档仓储（校验文档存在性）
     * @param grantRepository             文档授权管理仓储
     * @param auditEventRepository        审计事件仓储
     */
    public ReplaceMemberDocumentGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            WorkspaceMemberRepository workspaceMemberRepository,
            DocumentRepository documentRepository,
            DocumentGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.documentRepository = documentRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行成员文档授权批量替换。
     *
     * @param command 替换命令，包含目标成员 ID 和期望授权列表
     * @return 替换后的完整授权列表
     */
    @Override
    @Transactional
    public List<DocumentGrantResult> handle(ReplaceMemberDocumentGrantsCommand command) {
        // 1. 权限校验 + 目标成员定位
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + command.normalizedUserId()));
        // 治理边界：确保操作者有权管理目标成员角色的授权
        workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());

        // 2. 构建期望授权映射（documentId → permission），同时校验文档存在性
        Map<String, DocumentPermission> desiredAssignments = new LinkedHashMap<>();
        for (ReplaceMemberDocumentGrantsCommand.Assignment assignment : command.assignments()) {
            if (documentRepository.findById(currentUser.workspaceId(), new DocumentId(assignment.normalizedDocumentId())).isEmpty()) {
                throw new ManagedDocumentNotFoundException("document not found: " + assignment.normalizedDocumentId());
            }
            desiredAssignments.put(assignment.normalizedDocumentId(), assignment.resolvedPermission());
        }

        // 3. 查询现有授权，建立 documentId → grant 索引
        List<DocumentGrant> existingGrants = grantRepository.findActiveGrantsByUser(
                currentUser.workspaceId(),
                member.userId());
        Map<String, DocumentGrant> existingByDocumentId = new LinkedHashMap<>();
        existingGrants.forEach(grant -> existingByDocumentId.put(grant.documentId(), grant));

        // 4. 对比差异并执行变更（新增 / 更新 / 禁用 / 不变）
        int createdCount = 0;
        int updatedCount = 0;
        int disabledCount = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, DocumentPermission> entry : desiredAssignments.entrySet()) {
            DocumentGrant existing = existingByDocumentId.get(entry.getKey());
            if (existing == null) {
                // documentId 不在现有授权中 → 新增
                createdCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), entry.getKey(), member.userId(), entry.getValue(), now);
                continue;
            }
            if (existing.permission() != entry.getValue()) {
                // documentId 相同但权限不同 → 更新
                updatedCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), entry.getKey(), member.userId(), entry.getValue(), now);
            }
            // documentId 和权限均相同 → 跳过
        }
        for (DocumentGrant existing : existingGrants) {
            // 现有授权中的 documentId 不在期望授权中 → 禁用
            if (!desiredAssignments.containsKey(existing.documentId())
                    && grantRepository.disableGrant(currentUser.workspaceId(), existing.documentId(), member.userId(), now)) {
                disabledCount++;
            }
        }

        // 5. 审计记录
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "MEMBER_DOCUMENT_GRANTS_REPLACED",
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
                .map(grant -> new DocumentGrantResult(
                        grant.workspaceId(),
                        grant.documentId(),
                        grant.userId(),
                        grant.username(),
                        grant.displayName(),
                        grant.permission(),
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
