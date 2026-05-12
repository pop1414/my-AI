package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.GovernanceAccessDeniedException;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import org.springframework.stereotype.Service;

/**
 * 工作区治理边界守卫。
 *
 * <p>集中封装 OWNER / ADMIN / MEMBER 三级角色在治理后台中的变更边界规则，
 * 供账号治理、成员角色调整、资源授权等场景复用。所有规则围绕以下核心原则：
 *
 * <ul>
 *   <li><strong>OWNER 不可触碰：</strong>任何角色均不可降级、停用或移除 OWNER；</li>
 *   <li><strong>ADMIN 互不可管：</strong>ADMIN 之间互相不可操作，仅 OWNER 可管理 ADMIN；</li>
 *   <li><strong>最小提权：</strong>ADMIN 不可创建或提升其他成员为 ADMIN。</li>
 * </ul>
 *
 * <p>调用约定：
 * <ul>
 *   <li>调用方需先通过 {@link AuthorizationService#requireCanManageWorkspace()}
 *       确保操作者已具备基础管理权限；</li>
 *   <li>本守卫在此基础上叠加角色边界规则，通过则继续执行，不通过则抛出
 *       {@link GovernanceAccessDeniedException}。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class WorkspaceGovernanceGuard {

    /**
     * 校验操作者是否可以创建指定角色的托管账号。
     *
     * <p>规则：
     * <ul>
     *   <li>禁止创建 OWNER —— 当前版本不支持新增或转移 OWNER；</li>
     *   <li>仅 OWNER 可创建 ADMIN —— ADMIN 不可创建同级或更高级别账号。</li>
     * </ul>
     *
     * @param actor      操作者（当前登录用户）
     * @param targetRole 拟创建账号的目标工作区角色
     * @throws GovernanceAccessDeniedException 违反治理规则时抛出
     */
    public void requireCanCreateManagedAccount(CurrentUser actor, WorkspaceRole targetRole) {
        // 禁止创建 OWNER 角色账号
        if (targetRole == WorkspaceRole.WORKSPACE_OWNER) {
            throw deny(
                    "owner_immutable",
                    "当前版本不支持新增或转移 OWNER 角色");
        }
        // 非 OWNER 不可创建 ADMIN 账号
        if (targetRole == WorkspaceRole.WORKSPACE_ADMIN
                && actor.workspaceRole() != WorkspaceRole.WORKSPACE_OWNER) {
            throw deny(
                    "owner_only_admin_management",
                    "仅 OWNER 可创建或管理 ADMIN 角色");
        }
    }

    /**
     * 校验操作者是否可以管理（停用/启用/重置密码/移除）指定角色的托管账号。
     *
     * <p>规则：
     * <ul>
     *   <li>禁止操作 OWNER —— 任何人均不可触碰 OWNER 账号；</li>
     *   <li>ADMIN 不可操作其他 ADMIN —— 同级互不可管。</li>
     * </ul>
     *
     * @param actor      操作者（当前登录用户）
     * @param targetRole 目标账号的工作区角色
     * @throws GovernanceAccessDeniedException 违反治理规则时抛出
     */
    public void requireCanManageManagedAccount(CurrentUser actor, WorkspaceRole targetRole) {
        // 禁止操作 OWNER 账号
        if (targetRole == WorkspaceRole.WORKSPACE_OWNER) {
            throw denyForTargetOwner(actor.workspaceRole());
        }
        // ADMIN 不可操作其他 ADMIN 账号
        if (targetRole == WorkspaceRole.WORKSPACE_ADMIN
                && actor.workspaceRole() != WorkspaceRole.WORKSPACE_OWNER) {
            throw deny(
                    "admin_cannot_manage_admin",
                    "ADMIN 无权操作其他 ADMIN 账号或成员");
        }
    }

    /**
     * 校验操作者是否可以管理授权 grant（知识库授权 / 文档授权）。
     *
     * <p>规则与 {@link #requireCanManageManagedAccount} 一致：
     * 禁止触碰 OWNER，ADMIN 之间互不可管。
     *
     * @param actor      操作者（当前登录用户）
     * @param targetRole 授权目标用户的工作区角色
     * @throws GovernanceAccessDeniedException 违反治理规则时抛出
     */
    public void requireCanManageGrantTarget(CurrentUser actor, WorkspaceRole targetRole) {
        // 禁止对 OWNER 进行授权操作
        if (targetRole == WorkspaceRole.WORKSPACE_OWNER) {
            throw denyForTargetOwner(actor.workspaceRole());
        }
        // ADMIN 不可管理其他 ADMIN 的授权
        if (targetRole == WorkspaceRole.WORKSPACE_ADMIN
                && actor.workspaceRole() != WorkspaceRole.WORKSPACE_OWNER) {
            throw deny(
                    "admin_cannot_manage_admin",
                    "ADMIN 无权操作其他 ADMIN 账号或成员");
        }
    }

    /**
     * 校验操作者是否可以调整指定成员的工作区角色。
     *
     * <p>这是最复杂的一条规则，涉及三方角色（操作者、目标当前角色、目标新角色）：
     *
     * <ul>
     *   <li><strong>OWNER 不可触碰：</strong>涉及 OWNER 的变更一律拒绝：
     *     <ul>
     *       <li>目标当前是 OWNER → 拒绝（OWNER 不可被降级）</li>
     *       <li>目标新角色是 OWNER → 拒绝（不可提权为 OWNER）</li>
     *     </ul>
     *   </li>
     *   <li><strong>ADMIN 操作边界：</strong>若操作者本身是 ADMIN：
     *     <ul>
     *       <li>不可操作当前为 ADMIN 的成员（同级不可管）</li>
     *       <li>不可将成员提升为 ADMIN（仅 OWNER 可创建 ADMIN）</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param actor       操作者（当前登录用户）
     * @param currentRole 目标成员当前的工作区角色
     * @param targetRole  期望变更到的目标角色
     * @throws GovernanceAccessDeniedException 违反治理规则时抛出
     */
    public void requireCanUpdateWorkspaceRole(
            CurrentUser actor,
            WorkspaceRole currentRole,
            WorkspaceRole targetRole) {
        // ─── OWNER 不可触碰：任何涉及 OWNER 的变更一律拒绝 ───
        if (currentRole == WorkspaceRole.WORKSPACE_OWNER
                || targetRole == WorkspaceRole.WORKSPACE_OWNER) {
            // 区分拒绝消息：ADMIN 试图操作 OWNER 时给出更明确的提示
            if (currentRole == WorkspaceRole.WORKSPACE_OWNER
                    && actor.workspaceRole() == WorkspaceRole.WORKSPACE_ADMIN) {
                throw deny(
                        "admin_cannot_manage_owner",
                        "ADMIN 无权操作 OWNER 账号或成员");
            }
            // OWNER 操作 OWNER 或其他情况：统一提示 OWNER 不可变更
            throw deny(
                    "owner_immutable",
                    "OWNER 角色在当前版本中不可变更、不可停用、不可移除");
        }

        // ─── ADMIN 操作边界：ADMIN 操作者仅能管理 MEMBER ───
        if (actor.workspaceRole() == WorkspaceRole.WORKSPACE_ADMIN) {
            // ADMIN 不可操作当前为 ADMIN 的成员
            if (currentRole == WorkspaceRole.WORKSPACE_ADMIN) {
                throw deny(
                        "admin_cannot_manage_admin",
                        "ADMIN 无权操作其他 ADMIN 账号或成员");
            }
            // ADMIN 不可将 MEMBER 提升为 ADMIN
            if (targetRole == WorkspaceRole.WORKSPACE_ADMIN) {
                throw deny(
                        "owner_only_admin_management",
                        "仅 OWNER 可创建或管理 ADMIN 角色");
            }
        }
        // OWNER 操作任意角色（OWNER→ADMIN、ADMIN→MEMBER、MEMBER→ADMIN 等）—— 全部放行
    }

    /**
     * 构建"目标为 OWNER"场景下的拒绝异常，根据操作者角色给出差异化消息。
     *
     * @param actorRole 操作者角色
     * @return 包含适当拒绝原因的异常
     */
    private GovernanceAccessDeniedException denyForTargetOwner(WorkspaceRole actorRole) {
        if (actorRole == WorkspaceRole.WORKSPACE_ADMIN) {
            return deny(
                    "admin_cannot_manage_owner",
                    "ADMIN 无权操作 OWNER 账号或成员");
        }
        return deny(
                "owner_immutable",
                "OWNER 角色在当前版本中不可变更、不可停用、不可移除");
    }

    /**
     * 构建治理访问拒绝异常的统一工厂方法。
     *
     * @param reasonCode 拒绝原因码（如 owner_immutable、admin_cannot_manage_admin）
     * @param message    客户端可见的拒绝消息
     * @return 治理访问拒绝异常实例
     */
    private static GovernanceAccessDeniedException deny(String reasonCode, String message) {
        return new GovernanceAccessDeniedException(reasonCode, message);
    }
}
