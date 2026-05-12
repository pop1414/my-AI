package io.github.spike.myai.auth.application.result;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;

/**
 * 工作区成员结果对象。
 * <p>
 * 用例层返回的不可变数据传输对象，用于在应用服务与控制器之间传递成员信息。
 * 设计为 Record，字段只读，天然线程安全。
 * <p>
 * 与领域模型 {@link io.github.spike.myai.auth.domain.model.WorkspaceMember} 的区别：
 * 本对象属于应用层 DTO，不含任何业务行为，仅作为查询和更新操作的返回值载体。
 *
 * @param userId           用户唯一标识
 * @param username         用户名
 * @param displayName      展示名称（用于 UI 呈现）
 * @param workspaceId      所属工作区 ID
 * @param workspaceRole    工作区角色枚举
 * @param membershipStatus 成员关系状态（如 ACTIVE / INACTIVE）
 * @author spike
 * @since 1.0.0
 */
public record WorkspaceMemberResult(
        String userId,
        String username,
        String displayName,
        String workspaceId,
        WorkspaceRole workspaceRole,
        String membershipStatus) {
}
