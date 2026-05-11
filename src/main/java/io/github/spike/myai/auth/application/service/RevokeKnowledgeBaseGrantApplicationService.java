package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.RevokeKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.KnowledgeBaseGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.usecase.RevokeKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 回收知识库授权应用服务。
 * <p>
 * 实现 {@link RevokeKnowledgeBaseGrantUseCase} 用例，核心职责包括：
 * <ol>
 *   <li>权限校验：确保调用方具备工作区管理权限</li>
 *   <li>知识库存在性校验：确认目标知识库在当前工作区中存在</li>
 *   <li>授权查找：定位目标活跃授权记录</li>
 *   <li>软删除执行：将授权状态从 {@code ACTIVE} 变更为 {@code DISABLED}</li>
 *   <li>审计追踪：记录回收操作的审计事件及被回收授权的元数据</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class RevokeKnowledgeBaseGrantApplicationService implements RevokeKnowledgeBaseGrantUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    /** 知识库仓储，用于校验知识库存在性 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    /** 工作区成员仓储，用于校验目标用户当前角色 */
    private final WorkspaceMemberRepository workspaceMemberRepository;
    /** 知识库授权治理仓储 */
    private final KnowledgeBaseGrantManagementRepository grantRepository;
    /** 审计事件持久化仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService    授权服务
     * @param knowledgeBaseRepository 知识库仓储
     * @param grantRepository         授权治理仓储
     * @param auditEventRepository    审计事件仓储
     */
    public RevokeKnowledgeBaseGrantApplicationService(
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
     * 执行回收知识库授权用例。
     * <p>
     * 完整处理流程如下：
     * <ol>
     *   <li>校验当前用户是否具备工作区管理权限</li>
     *   <li>校验目标知识库是否存在</li>
     *   <li>查找目标活跃授权记录，不存在则快速失败</li>
     *   <li>执行软删除（状态变更为 DISABLED）</li>
     *   <li>若更新失败（并发场景下记录已被回收），抛出异常</li>
     *   <li>记录审计事件，持久化被回收授权的元数据</li>
     * </ol>
     *
     * @param command 回收命令，包含知识库 ID 和目标用户 ID
     * @throws KnowledgeBaseGrantNotFoundException 当目标授权不存在或已被回收时抛出
     * @throws ManagedKnowledgeBaseNotFoundException 当知识库不存在时抛出
     */
    @Override
    public void handle(RevokeKnowledgeBaseGrantCommand command) {
        // Step 1: 校验当前用户的工作区管理权限
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // Step 2: 确认目标知识库存在
        ensureKnowledgeBaseExists(currentUser.workspaceId(), command.normalizedKbId());

        // Step 3: 查找目标活跃授权记录，不存在则快速失败
        KnowledgeBaseGrant existingGrant = grantRepository.findActiveGrant(
                        currentUser.workspaceId(),
                        command.normalizedKbId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new KnowledgeBaseGrantNotFoundException(
                        "knowledge base grant not found: " + command.normalizedKbId() + "/" + command.normalizedUserId()));
        WorkspaceMember targetMember = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new KnowledgeBaseGrantNotFoundException(
                        "knowledge base grant not found: " + command.normalizedKbId() + "/" + command.normalizedUserId()));
        workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, targetMember.workspaceRole());

        // Step 4: 获取当前时间戳，用于统一标记操作时间
        Instant now = Instant.now();

        // Step 5: 执行软删除，将授权状态变更为 DISABLED
        boolean updated = grantRepository.disableGrant(
                currentUser.workspaceId(),
                command.normalizedKbId(),
                command.normalizedUserId(),
                now);
        // 更新失败（如并发回收），抛出异常告知调用方
        if (!updated) {
            throw new KnowledgeBaseGrantNotFoundException(
                    "knowledge base grant not found: " + command.normalizedKbId() + "/" + command.normalizedUserId());
        }

        // Step 6: 持久化审计事件，记录被回收授权的元数据
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "KNOWLEDGE_BASE_GRANT_REVOKED",
                "KNOWLEDGE_BASE_GRANT",
                command.normalizedKbId() + ":" + command.normalizedUserId(),
                "SUCCESS",
                "",
                buildRevokeMetadata(command.normalizedKbId(), existingGrant),
                now));
    }

    /**
     * 校验知识库在当前工作区中是否存在。
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
     * 构建回收授权审计元数据 JSON 字符串。
     * <p>
     * 包含知识库 ID、被回收用户 ID、用户名及被回收的角色四个字段，
     * 各字段值经过 JSON 字符串转义处理。
     *
     * @param kbId          知识库 ID
     * @param existingGrant 回收前的授权记录
     * @return JSON 格式的元数据字符串
     */
    private static String buildRevokeMetadata(String kbId, KnowledgeBaseGrant existingGrant) {
        return """
                {"kbId":%s,"targetUserId":%s,"targetUsername":%s,"revokedRole":%s}
                """.formatted(
                toJsonString(kbId),
                toJsonString(existingGrant.userId()),
                toJsonString(existingGrant.username()),
                toJsonString(existingGrant.role().name()));
    }

    /**
     * 将字符串包装为合法的 JSON 字符串值（含双引号并转义特殊字符）。
     * <p>
     * 先转义反斜杠，再转义双引号，顺序不可颠倒。
     *
     * @param value 原始字符串值
     * @return 带双引号且已转义的 JSON 字符串值
     */
    private static String toJsonString(String value) {
        // 先转义反斜杠，再转义双引号（顺序不可颠倒，否则会产生多余转义）
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
