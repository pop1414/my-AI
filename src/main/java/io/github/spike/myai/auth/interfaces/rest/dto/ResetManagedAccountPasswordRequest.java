package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 重置密码请求体。
 *
 * @param password 新密码
 */
public record ResetManagedAccountPasswordRequest(String password) {
}
