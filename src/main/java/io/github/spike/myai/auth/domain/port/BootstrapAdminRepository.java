package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.BootstrapAdminAccount;

/**
 * 初始管理员账号引导仓储端口（出站端口）。
 *
 * <p>定义空库引导场景下的持久化契约，位于六边形架构的领域层。
 * 应用服务仅依赖此接口，不感知底层 SQL 实现细节。
 *
 * <p>当前唯一实现：
 * {@link io.github.spike.myai.auth.infrastructure.persistence.JdbcBootstrapAdminRepository}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface BootstrapAdminRepository {

    /**
     * 统计指定工作空间下的成员数量。
     *
     * <p>用于判断默认工作空间是否已有成员，以决定是否需要引导创建管理员。
     *
     * @param workspaceId 工作空间 ID
     * @return 成员关系数量（0 表示空工作空间，需要引导）
     */
    int countWorkspaceMemberships(String workspaceId);

    /**
     * 写入或补齐初始管理员账号。
     *
     * <p>实现需在单个事务中完成三表的 UPSERT 操作：
     * <ol>
     *   <li>{@code users} —— 创建或激活用户记录；</li>
     *   <li>{@code local_credentials} —— 写入或更新密码凭证；</li>
     *   <li>{@code workspace_memberships} —— 建立工作空间成员关系。</li>
     * </ol>
     *
     * <p>使用 UPSERT 语义而非纯 INSERT，确保服务重启或并发启动时的幂等性：
     * 重复调用不会产生重复记录或主键冲突。
     *
     * @param account 初始管理员账号写入模型
     * @return 实际写入或复用的用户 ID（首次为随机 UUID，幂等调用返回已有 ID）
     */
    String saveBootstrapAdmin(BootstrapAdminAccount account);
}
