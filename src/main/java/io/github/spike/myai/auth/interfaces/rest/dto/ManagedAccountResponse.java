package io.github.spike.myai.auth.interfaces.rest.dto;

import java.time.Instant;

/**
 * 托管账号响应体。
 *
 * <p>包含账号治理所需的全部字段，由 Controller 层从用例结果转换后返回给前端。
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
public record ManagedAccountResponse(
        String userId,
        String username,
        String displayName,
        String userStatus,
        String workspaceId,
        String workspaceRole,
        String membershipStatus,
        int failedLoginCount,
        Instant lockedUntil) {
}
