package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 更新账号状态请求体。
 *
 * @param userStatus 目标状态，仅允许 ACTIVE 或 DISABLED
 */
public record UpdateManagedAccountStatusRequest(String userStatus) {
}
