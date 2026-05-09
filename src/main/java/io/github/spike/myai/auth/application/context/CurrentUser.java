package io.github.spike.myai.auth.application.context;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;

/**
 * 应用层当前用户上下文。
 *
 * <p>该模型是 Spring Security 认证信息在应用层的投影，用于业务应用服务
 * 读取当前登录用户。设计上刻意剥离了对 Spring Security
 * {@code Authentication} / {@code Principal} 的直接依赖，
 * 使应用层保持框架无关性。
 *
 * <p>与 {@link io.github.spike.myai.auth.application.result.CurrentUserResult}
 * 的区别：
 * <ul>
 *   <li>{@code CurrentUser} —— 从安全上下文读取，供业务服务消费；</li>
 *   <li>{@code CurrentUserResult} —— 登录用例的返回值，供控制器转换 DTO。</li>
 * </ul>
 *
 * <p>使用 Java {@code record} 保证不可变性。
 *
 * <p>{@code workspaceRole} 字段使用领域枚举 {@link WorkspaceRole}
 * 而非原始字符串，提供类型安全和角色判断方法。
 *
 * @param userId        用户唯一标识（业务主键）
 * @param username      用户名
 * @param workspaceId   当前工作空间 ID
 * @param workspaceRole 当前工作空间角色（使用领域枚举类型）
 * @author spike
 * @since 1.0.0
 */
public record CurrentUser(
        /** 用户唯一标识（业务主键） */
        String userId,
        /** 用户名 */
        String username,
        /** 当前工作空间 ID */
        String workspaceId,
        /** 当前工作空间角色，使用领域枚举提供类型安全 */
        WorkspaceRole workspaceRole) {
}
