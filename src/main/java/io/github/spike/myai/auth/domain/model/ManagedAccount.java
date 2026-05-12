package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 账号治理读模型。
 *
 * <p>聚合用户基础信息、工作区成员关系和登录锁定状态，
 * 供治理后台执行账号管理操作时使用。
 *
 * @param userId 用户唯一标识
 * @param username 用户名
 * @param displayName 展示名称
 * @param userStatus 用户状态
 * @param workspaceId 工作区 ID
 * @param workspaceRole 工作区角色
 * @param membershipStatus 成员关系状态
 * @param failedLoginCount 连续登录失败次数
 * @param lockedUntil 锁定截止时间
 */
public record ManagedAccount(
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
