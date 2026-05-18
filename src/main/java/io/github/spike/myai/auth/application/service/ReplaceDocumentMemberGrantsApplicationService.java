package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.ReplaceDocumentMemberGrantsCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ReplaceDocumentMemberGrantsUseCase;
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
 * 以文档为维度批量覆盖成员授权应用服务。
 *
 * <p>实现 {@link ReplaceDocumentMemberGrantsUseCase} 用例，
 * 将指定文档的成员授权集合整体替换为传入的期望授权列表。
 *
 * <h3>替换语义（声明式同步）</h3>
 * <ol>
 *   <li><strong>新增：</strong>期望授权中的 userId 不在现有授权中 → 新建 grant</li>
 *   <li><strong>更新：</strong>期望授权与现有授权 userId 相同但权限不同 → 覆写 grant</li>
 *   <li><strong>禁用：</strong>现有授权中的 userId 不在期望授权中 → 软删除 grant</li>
 *   <li><strong>不变：</strong>期望授权与现有授权 userId 和权限均相同 → 跳过</li>
 * </ol>
 * 整个过程在同一数据库事务中完成。
 */
@Service
public class ReplaceDocumentMemberGrantsApplicationService implements ReplaceDocumentMemberGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard;
    private final DocumentRepository documentRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DocumentGrantManagementRepository grantRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService        授权服务
     * @param workspaceGovernanceGuard    工作区治理边界守卫
     * @param documentGrantKnowledgeBaseGuard 文档授权父级知识库授权守卫
     * @param documentRepository          文档仓储
     * @param workspaceMemberRepository   工作区成员仓储
     * @param grantRepository             文档授权管理仓储
     * @param auditEventRepository        审计事件仓储
     */
    public ReplaceDocumentMemberGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard,
            DocumentRepository documentRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            DocumentGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.documentGrantKnowledgeBaseGuard = documentGrantKnowledgeBaseGuard;
        this.documentRepository = documentRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行文档成员授权批量替换。
     *
     * @param command 替换命令，包含目标文档 ID 和期望授权列表
     * @return 替换后的完整授权列表
     */
    @Override
    @Transactional
    public List<DocumentGrantResult> handle(ReplaceDocumentMemberGrantsCommand command) {
        // 1. 权限校验 + 文档存在性校验
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        String documentId = command.normalizedDocumentId();
        var document = documentRepository.findById(currentUser.workspaceId(), new DocumentId(documentId))
                .orElseThrow(() -> new ManagedDocumentNotFoundException("document not found: " + documentId));

        // 2. 构建期望授权映射（userId → permission），同时校验成员存在性 + 治理边界
        Map<String, DocumentPermission> desiredAssignments = new LinkedHashMap<>();
        for (ReplaceDocumentMemberGrantsCommand.Assignment assignment : command.assignments()) {
            WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                            currentUser.workspaceId(),
                            assignment.normalizedUserId())
                    .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + assignment.normalizedUserId()));
            // 治理边界：确保操作者有权管理目标成员角色的授权
            workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());
            // 文档权限覆盖必须依附于目标成员已拥有的知识库授权，避免产生孤儿授权。
            documentGrantKnowledgeBaseGuard.requireMemberKnowledgeBaseGrant(
                    currentUser.workspaceId(),
                    member.userId(),
                    document.kbId());
            desiredAssignments.put(member.userId(), assignment.resolvedPermission());
        }

        // 3. 查询现有授权，建立 userId → grant 索引
        List<DocumentGrant> existingGrants = grantRepository.findActiveGrants(currentUser.workspaceId(), documentId);
        Map<String, DocumentGrant> existingByUserId = new LinkedHashMap<>();
        existingGrants.forEach(grant -> existingByUserId.put(grant.userId(), grant));

        // 4. 对比差异并执行变更（新增 / 更新 / 禁用 / 不变）
        int createdCount = 0;
        int updatedCount = 0;
        int disabledCount = 0;
        Instant now = Instant.now();
        for (Map.Entry<String, DocumentPermission> entry : desiredAssignments.entrySet()) {
            DocumentGrant existing = existingByUserId.get(entry.getKey());
            if (existing == null) {
                // userId 不在现有授权中 → 新增
                createdCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), documentId, entry.getKey(), entry.getValue(), now);
                continue;
            }
            if (existing.permission() != entry.getValue()) {
                // userId 相同但权限不同 → 更新
                updatedCount++;
                grantRepository.saveGrant(currentUser.workspaceId(), documentId, entry.getKey(), entry.getValue(), now);
            }
            // userId 和权限均相同 → 跳过
        }
        for (DocumentGrant existing : existingGrants) {
            // 现有授权中的 userId 不在期望授权中 → 禁用
            if (!desiredAssignments.containsKey(existing.userId())
                    && grantRepository.disableGrant(currentUser.workspaceId(), documentId, existing.userId(), now)) {
                disabledCount++;
            }
        }

        // 5. 审计记录
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "DOCUMENT_MEMBER_GRANTS_REPLACED",
                "DOCUMENT",
                documentId,
                "SUCCESS",
                "",
                """
                {"documentId":%s,"createdCount":%s,"updatedCount":%s,"disabledCount":%s}
                """.formatted(
                        toJsonString(documentId),
                        String.valueOf(createdCount),
                        String.valueOf(updatedCount),
                        String.valueOf(disabledCount)),
                now));

        // 6. 回查最新授权列表并返回
        return grantRepository.findActiveGrants(currentUser.workspaceId(), documentId).stream()
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
