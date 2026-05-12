package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.UpsertKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.UpsertKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 授予或更新知识库授权应用服务。
 * <p>
 * 实现 {@link UpsertKnowledgeBaseGrantUseCase} 用例，核心职责包括：
 * <ol>
 *   <li>权限校验：确保调用方具备工作区管理权限</li>
 *   <li>知识库存在性校验：确认目标知识库在当前工作区中存在</li>
 *   <li>成员校验：确认目标用户为当前工作区的活跃成员</li>
 *   <li>幂等处理：若已存在授权且角色未变更，直接返回</li>
 *   <li>Upsert 执行：通过仓储层执行插入或更新（INSERT ON CONFLICT DO UPDATE）</li>
 *   <li>审计追踪：记录授权变更的审计事件，包含变更前后角色元数据</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class UpsertKnowledgeBaseGrantApplicationService implements UpsertKnowledgeBaseGrantUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    /** 知识库仓储，用于校验知识库存在性 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    /** 工作区成员仓储，用于校验目标用户是否为活跃成员 */
    private final WorkspaceMemberRepository workspaceMemberRepository;
    /** 知识库授权治理仓储 */
    private final KnowledgeBaseGrantManagementRepository grantRepository;
    /** 审计事件持久化仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService      授权服务
     * @param knowledgeBaseRepository   知识库仓储
     * @param workspaceMemberRepository 工作区成员仓储
     * @param grantRepository           授权治理仓储
     * @param auditEventRepository      审计事件仓储
     */
    public UpsertKnowledgeBaseGrantApplicationService(
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
     * 执行授予或更新知识库授权用例。
     * <p>
     * 完整处理流程如下：
     * <ol>
     *   <li>校验当前用户是否具备工作区管理权限</li>
     *   <li>校验目标知识库是否存在</li>
     *   <li>校验目标用户是否为活跃工作区成员</li>
     *   <li>解析目标角色枚举值</li>
     *   <li>查询现有授权记录</li>
     *   <li>幂等判断：若已有授权且角色一致，直接返回</li>
     *   <li>执行 Upsert 操作（INSERT ON CONFLICT DO UPDATE）</li>
     *   <li>记录审计事件</li>
     *   <li>返回包含新角色信息的结果对象</li>
     * </ol>
     *
     * @param command 授权命令，包含知识库 ID、用户 ID 和目标角色
     * @return 操作后的授权结果
     * @throws WorkspaceMemberNotFoundException     当目标用户不是活跃工作区成员时抛出
     * @throws ManagedKnowledgeBaseNotFoundException 当知识库不存在时抛出
     * @throws IllegalArgumentException              当命令中角色无效时抛出
     */
    @Override
    public KnowledgeBaseGrantResult handle(UpsertKnowledgeBaseGrantCommand command) {
        // Step 1: 校验当前用户的工作区管理权限
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // Step 2: 确认目标知识库存在
        ensureKnowledgeBaseExists(currentUser.workspaceId(), command.normalizedKbId());

        // Step 3: 校验目标用户是否为活跃工作区成员
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "workspace member not found: " + command.normalizedUserId()));
        workspaceGovernanceGuard.requireCanManageGrantTarget(
                currentUser,
                member.workspaceRole());

        // Step 4: 解析目标角色枚举值
        KnowledgeBaseRole targetRole = command.resolvedRole();

        // Step 5: 查询现有授权记录（可能为空）
        Optional<KnowledgeBaseGrant> existingGrant = grantRepository.findActiveGrant(
                currentUser.workspaceId(),
                command.normalizedKbId(),
                member.userId());

        // Step 6: 幂等检查——已有授权且角色未变更，直接返回
        if (existingGrant.filter(grant -> grant.role() == targetRole).isPresent()) {
            return toResult(existingGrant.get());
        }

        // Step 7: 获取当前时间戳
        Instant now = Instant.now();

        // Step 8: 执行 Upsert（存在则更新角色，不存在则插入新记录）
        grantRepository.saveGrant(
                currentUser.workspaceId(),
                command.normalizedKbId(),
                member.userId(),
                targetRole,
                now);

        // Step 9: 持久化审计事件
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "KNOWLEDGE_BASE_GRANT_UPSERTED",
                "KNOWLEDGE_BASE_GRANT",
                command.normalizedKbId() + ":" + member.userId(),
                "SUCCESS",
                "",
                buildUpsertMetadata(command.normalizedKbId(), member, existingGrant, targetRole),
                now));

        // Step 10: 构造并返回结果（状态固定为 ACTIVE）
        return new KnowledgeBaseGrantResult(
                currentUser.workspaceId(),
                command.normalizedKbId(),
                member.userId(),
                member.username(),
                member.displayName(),
                targetRole,
                "ACTIVE");
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
     * 将领域模型 {@link KnowledgeBaseGrant} 转换为用例层结果。
     *
     * @param grant 知识库授权领域对象
     * @return 用例层结果对象
     */
    private static KnowledgeBaseGrantResult toResult(KnowledgeBaseGrant grant) {
        return new KnowledgeBaseGrantResult(
                grant.workspaceId(),
                grant.kbId(),
                grant.userId(),
                grant.username(),
                grant.displayName(),
                grant.role(),
                grant.status());
    }

    /**
     * 构建 Upsert 授权审计元数据 JSON 字符串。
     * <p>
     * 包含知识库 ID、目标用户 ID、用户名、变更前角色（可能为 {@code null} 表示新建）
     * 及变更后角色五个字段，各字段值经过 JSON 字符串转义处理。
     *
     * @param kbId          知识库 ID
     * @param member        目标工作区成员
     * @param existingGrant 变更前的授权记录（可能为空，表示新建授权）
     * @param targetRole    目标角色
     * @return JSON 格式的元数据字符串
     */
    private static String buildUpsertMetadata(
            String kbId,
            WorkspaceMember member,
            Optional<KnowledgeBaseGrant> existingGrant,
            KnowledgeBaseRole targetRole) {
        return """
                {"kbId":%s,"targetUserId":%s,"targetUsername":%s,"previousRole":%s,"newRole":%s}
                """.formatted(
                toJsonString(kbId),
                toJsonString(member.userId()),
                toJsonString(member.username()),
                // 若无现有授权（新建场景），previousRole 为 null（JSON 字面量）
                existingGrant.map(KnowledgeBaseGrant::role).map(KnowledgeBaseRole::name).map(UpsertKnowledgeBaseGrantApplicationService::toJsonString).orElse("null"),
                toJsonString(targetRole.name()));
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
        // 先转义反斜杠，再转义双引号（顺序不可颠倒）
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
