package io.github.spike.myai.auth.application.exception;

import io.github.spike.myai.auth.application.service.WorkspaceGovernanceGuard;
import org.springframework.security.access.AccessDeniedException;

/**
 * 工作区治理授权拒绝异常。
 *
 * <p>继承 Spring Security 的 {@link AccessDeniedException}，用于表达治理后台中
 * OWNER / ADMIN / MEMBER 三级角色的层级边界拒绝原因。与普通的 403 不同，
 * 本异常携带结构化的 {@code reasonCode}，供
 * {@link io.github.spike.myai.shared.rest.GlobalRestExceptionHandler} 解析后
 * 透传给前端，便于展示差异化的拒绝提示。
 *
 * <h3>典型 reasonCode</h3>
 * <ul>
 *   <li>{@code owner_immutable} — OWNER 不可变更、停用或移除</li>
 *   <li>{@code admin_cannot_manage_owner} — ADMIN 无权操作 OWNER</li>
 *   <li>{@code admin_cannot_manage_admin} — ADMIN 无权操作其他 ADMIN</li>
 *   <li>{@code owner_only_admin_management} — 仅 OWNER 可管理 ADMIN</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public class GovernanceAccessDeniedException extends AccessDeniedException {

    /** 拒绝原因码，由 {@link WorkspaceGovernanceGuard} 在拒绝时填充 */
    private final String reasonCode;

    /**
     * 构造治理授权拒绝异常。
     *
     * @param reasonCode 拒绝原因码（如 owner_immutable）
     * @param message    客户端可见的拒绝描述消息
     */
    public GovernanceAccessDeniedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    /**
     * 返回拒绝原因码，供异常处理器提取后透传至前端。
     *
     * @return 拒绝原因码
     */
    public String reasonCode() {
        return reasonCode;
    }
}
