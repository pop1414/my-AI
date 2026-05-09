package io.github.spike.myai.auth.application.result;

import io.github.spike.myai.auth.domain.model.WorkspaceRole;

/**
 * 当前用户结果对象。
 *
 * <p>封装登录用例成功后的输出数据，由应用服务层返回给入站适配器，
 * 供其转换为 API 响应 DTO 或构建 Spring Security 认证主体。
 * 使用 Java {@code record} 类型保证不可变性。
 *
 * @param userId        用户唯一标识（业务主键）
 * @param username      用户名
 * @param displayName   展示名称（如昵称或真实姓名）
 * @param workspaceId    所属工作空间 ID
 * @param workspaceRole  工作空间角色
 * @author spike
 * @since 1.0.0
 */
public record CurrentUserResult(
        /** 用户唯一标识（业务主键） */
        String userId,
        /** 用户名 */
        String username,
        /** 展示名称，用于前端界面显示 */
        String displayName,
        /** 所属工作空间 ID */
        String workspaceId,
        /** 工作空间角色 */
        WorkspaceRole workspaceRole) {
}
