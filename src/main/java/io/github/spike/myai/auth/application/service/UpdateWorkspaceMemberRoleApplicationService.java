package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.UpdateWorkspaceMemberRoleUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 调整工作区成员角色应用服务。
 * <p>
 * 实现 {@link UpdateWorkspaceMemberRoleUseCase} 用例，核心职责包括：
 * <ol>
 *   <li>权限校验：确保调用方具备工作区管理权限</li>
 *   <li>幂等处理：若目标角色与当前角色一致则直接返回，避免无意义写操作</li>
 *   <li>角色更新：通过仓储层原子更新成员角色及更新时间戳</li>
 *   <li>审计追踪：记录角色变更的审计事件，包含变更前后元数据</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class UpdateWorkspaceMemberRoleApplicationService implements UpdateWorkspaceMemberRoleUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 工作区成员持久化仓储 */
    private final WorkspaceMemberRepository workspaceMemberRepository;
    /** 审计事件持久化仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService      授权服务
     * @param workspaceMemberRepository 工作区成员仓储
     * @param auditEventRepository      审计事件仓储
     */
    public UpdateWorkspaceMemberRoleApplicationService(
            AuthorizationService authorizationService,
            WorkspaceMemberRepository workspaceMemberRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行更新工作区成员角色用例。
     * <p>
     * 完整处理流程如下：
     * <ol>
     *   <li>校验当前用户是否具备工作区管理权限</li>
     *   <li>根据命令中的用户 ID 查找目标活跃成员，不存在则抛出 {@link WorkspaceMemberNotFoundException}</li>
     *   <li>解析命令中的目标角色枚举值</li>
     *   <li>若目标角色与当前角色一致，直接返回（幂等处理）</li>
     *   <li>调用仓储层更新角色，若更新失败（并发场景下记录可能已被删除）则抛出异常</li>
     *   <li>记录审计事件，持久化角色变更前后的元数据</li>
     *   <li>返回更新后的成员信息</li>
     * </ol>
     *
     * @param command 角色更新命令，包含目标用户 ID 和新角色
     * @return 更新后的工作区成员结果
     * @throws WorkspaceMemberNotFoundException 目标成员不存在或更新时已被删除
     */
    @Override
    public WorkspaceMemberResult handle(UpdateWorkspaceMemberRoleCommand command) {
        // Step 1: 校验当前用户的工作区管理权限，获取当前用户上下文
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // Step 2: 查找目标活跃成员，不存在则快速失败
        WorkspaceMember targetMember = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "workspace member not found: " + command.normalizedUserId()));

        // Step 3: 解析目标角色枚举值
        WorkspaceRole targetRole = command.resolvedWorkspaceRole();

        // Step 4: 幂等检查——角色未变更则直接返回，避免无意义的写操作和审计记录
        if (targetMember.workspaceRole() == targetRole) {
            return toResult(targetMember);
        }

        // Step 5: 获取当前时间戳，用于统一标记本次操作的更新时间
        Instant now = Instant.now();

        // Step 6: 通过仓储层原子更新角色，返回 boolean 表示是否成功更新到行
        boolean updated = workspaceMemberRepository.updateWorkspaceRole(
                currentUser.workspaceId(),
                targetMember.userId(),
                targetRole,
                now);
        // 更新失败（如并发删除），抛出异常告知调用方
        if (!updated) {
            throw new WorkspaceMemberNotFoundException(
                    "workspace member not found: " + command.normalizedUserId());
        }

        // Step 7: 持久化审计事件，记录角色变更前后的元数据
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "WORKSPACE_MEMBER_ROLE_UPDATED",
                "WORKSPACE_MEMBERSHIP",
                targetMember.userId(),
                "SUCCESS",
                "",
                buildRoleChangeMetadata(targetMember, targetRole),
                now));

        // Step 8: 构造包含新角色的成员对象并返回结果
        return toResult(new WorkspaceMember(
                targetMember.userId(),
                targetMember.username(),
                targetMember.displayName(),
                targetMember.workspaceId(),
                targetRole,
                targetMember.membershipStatus()));
    }

    /**
     * 将领域模型 {@link WorkspaceMember} 转换为用例层返回结果。
     *
     * @param member 工作区成员领域对象
     * @return 对应的用例层结果对象
     */
    private static WorkspaceMemberResult toResult(WorkspaceMember member) {
        return new WorkspaceMemberResult(
                member.userId(),
                member.username(),
                member.displayName(),
                member.workspaceId(),
                member.workspaceRole(),
                member.membershipStatus());
    }

    /**
     * 构建角色变更审计元数据 JSON 字符串。
     * <p>
     * 包含目标用户名、变更前角色、变更后角色三个字段，
     * 各字段值经过 JSON 字符串转义处理，避免注入破坏 JSON 结构。
     *
     * @param member     变更前的成员信息
     * @param targetRole 目标角色
     * @return JSON 格式的元数据字符串
     */
    private static String buildRoleChangeMetadata(WorkspaceMember member, WorkspaceRole targetRole) {
        return """
                {"targetUsername":%s,"previousRole":%s,"newRole":%s}
                """.formatted(
                toJsonString(member.username()),
                toJsonString(member.workspaceRole().name()),
                toJsonString(targetRole.name()));
    }

    /**
     * 将字符串包装为合法的 JSON 字符串值（含双引号并转义特殊字符）。
     * <p>
     * 处理逻辑：
     * <ol>
     *   <li>在字符串首尾添加双引号</li>
     *   <li>对反斜杠 {@code \} 进行转义，防止后续转义被误解</li>
     *   <li>对双引号 {@code "} 进行转义，防止提前闭合 JSON 字符串</li>
     * </ol>
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
