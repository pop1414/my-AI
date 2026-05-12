package io.github.spike.myai.auth.application.result;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.time.Instant;

/**
 * 账号治理结果对象。
 *
 * <p>从领域读模型 {@link io.github.spike.myai.auth.domain.model.ManagedAccount} 转换而来，
 * 作为用例层的输出 DTO，供 Controller 层转换为 HTTP 响应。
 *
 * @param userId           用户唯一标识
 * @param username         用户名
 * @param displayName      展示名称
 * @param userStatus       用户状态（ACTIVE / DISABLED）
 * @param workspaceId      工作区 ID
 * @param workspaceRole    工作区角色
 * @param membershipStatus 成员关系状态（ACTIVE / INACTIVE）
 * @param failedLoginCount 连续登录失败次数
 * @param lockedUntil      锁定截止时间，null 表示未锁定
 */
public record ManagedAccountResult(
        String userId,
        String username,
        String displayName,
        String userStatus,
        String workspaceId,
        WorkspaceRole workspaceRole,
        String membershipStatus,
        int failedLoginCount,
        Instant lockedUntil) {
}
