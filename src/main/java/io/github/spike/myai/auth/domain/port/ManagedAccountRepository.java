package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 账号治理仓储端口。
 *
 * <p>定义托管账号的持久化操作契约，由基础设施层的 JDBC 实现提供具体逻辑。
 * 所有查询和写操作均以工作区为隔离边界，确保多租户数据安全。
 */
public interface ManagedAccountRepository {

    /**
     * 查询指定工作区下所有托管账号。
     *
     * @param workspaceId 工作区 ID
     * @return 账号列表，按创建时间升序排列
     */
    List<ManagedAccount> findWorkspaceAccounts(String workspaceId);

    /**
     * 按工作区和用户 ID 查询单个托管账号。
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户唯一标识
     * @return 托管账号，不存在时返回 {@link Optional#empty()}
     */
    Optional<ManagedAccount> findWorkspaceAccount(String workspaceId, String userId);

    /**
     * 判断指定用户名是否已存在。
     *
     * @param username 用户名
     * @return true 表示用户名已被占用
     */
    boolean existsUsername(String username);

    /**
     * 创建托管账号。
     *
     * <p>在同一个事务中依次插入用户记录、本地凭据和工作区成员关系，
     * 创建完成后回查确认数据一致性。
     *
     * @param workspaceId   工作区 ID
     * @param username      用户名
     * @param displayName   展示名称
     * @param passwordHash  已编码的密码哈希
     * @param workspaceRole 工作区角色
     * @param now           当前时间（用于 created_at / updated_at）
     * @return 创建成功的托管账号读模型
     */
    ManagedAccount createAccount(
            String workspaceId,
            String username,
            String displayName,
            String passwordHash,
            WorkspaceRole workspaceRole,
            Instant now);

    /**
     * 更新用户状态（ACTIVE / DISABLED）。
     *
     * <p>仅当用户属于指定工作区时才执行更新，防止越权操作。
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户唯一标识
     * @param userStatus  目标状态
     * @param updatedAt   更新时间
     * @return true 表示更新成功，false 表示未找到匹配记录
     */
    boolean updateUserStatus(String workspaceId, String userId, String userStatus, Instant updatedAt);

    /**
     * 重置用户密码。
     *
     * <p>更新本地凭据中的密码哈希，同时清除登录锁定状态
     *（failedLoginCount 归零、lockedUntil 置空）。
     *
     * @param workspaceId  工作区 ID
     * @param userId       用户唯一标识
     * @param passwordHash 新的密码哈希
     * @param updatedAt    更新时间
     * @return true 表示更新成功，false 表示未找到匹配记录
     */
    boolean resetPassword(String workspaceId, String userId, String passwordHash, Instant updatedAt);

    /**
     * 将工作区成员关系标记为 INACTIVE。
     *
     * <p>仅当成员当前为 ACTIVE 状态时才执行更新，实现幂等性。
     *
     * @param workspaceId 工作区 ID
     * @param userId      用户唯一标识
     * @param updatedAt   更新时间
     * @return true 表示更新成功，false 表示未找到活跃成员关系
     */
    boolean deactivateMembership(String workspaceId, String userId, Instant updatedAt);
}
