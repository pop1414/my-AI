package io.github.spike.myai.auth.security;

import java.io.Serializable;

/**
 * 自定义用户主体（Principal），承载业务域的用户身份信息。
 *
 * <p>实现 Spring Security 的认证主体抽象，作为
 * {@link org.springframework.security.core.Authentication#getPrincipal()}
 * 的返回值，供控制器（如 {@code AuthController#me}）和后续过滤器使用。
 *
 * <p>设计考量：
 * <ul>
 *   <li>使用 Java {@code record} 类型，保证不可变性和简洁性；</li>
 *   <li>实现 {@link Serializable}，确保 Session 持久化/反序列化兼容；</li>
 *   <li>除基础身份字段（userId、username）外，还携带业务上下文信息
 *       （displayName、workspaceId、workspaceRole），减少后续查询开销。</li>
 * </ul>
 *
 * @param userId        用户唯一标识（业务主键）
 * @param username      用户名
 * @param displayName   展示名称（如昵称或真实姓名）
 * @param workspaceId    所属工作空间 ID
 * @param workspaceRole  工作空间角色（如 ADMIN、MEMBER）
 * @author spike
 * @since 1.0.0
 */
public record MyAiPrincipal(
        /** 用户唯一标识（业务主键） */
        String userId,
        /** 用户名 */
        String username,
        /** 展示名称，用于前端界面显示 */
        String displayName,
        /** 所属工作空间 ID */
        String workspaceId,
        /** 工作空间角色，映射为 ROLE_ 前缀的 Spring Security 权限 */
        String workspaceRole) implements Serializable {
}
