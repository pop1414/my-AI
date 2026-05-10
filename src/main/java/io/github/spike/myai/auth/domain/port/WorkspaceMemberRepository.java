package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 工作区成员治理仓储端口。
 * <p>
 * 定义成员查询与角色调整的持久化契约，供治理接口应用服务复用。
 * 所有方法均以"有效成员"为操作口径：同时要求用户状态和成员关系状态均为 {@code ACTIVE}。
 * <p>
 * 实现类需保证：
 * <ul>
 *   <li>查询方法在无匹配记录时返回空列表或 {@link Optional#empty()}，不抛异常</li>
 *   <li>更新方法返回 {@code boolean} 以区分"更新成功"与"无匹配行"两种语义</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public interface WorkspaceMemberRepository{

    /**
     * 查询指定工作区的有效成员列表。
     * <p>
     * 当前"有效成员"口径固定为：
     * <ul>
     *   <li>用户状态为 {@code ACTIVE}</li>
     *   <li>成员关系状态为 {@code ACTIVE}</li>
     * </ul>
     * 结果按创建时间升序排列，保证多次调用顺序一致。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @return 有效成员列表，若无匹配成员则返回空列表（非 {@code null}）
     */
    List<WorkspaceMember> findActiveMembers(String workspaceId);

    /**
     * 查询指定工作区中的单个有效成员。
     * <p>
     * 同时匹配工作区 ID、用户 ID 及双重 {@code ACTIVE} 状态条件。
     * 适用于角色更新前的成员存在性校验场景。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param userId      用户 ID，不能为空
     * @return 有效成员，不存在则返回 {@link Optional#empty()}
     */
    Optional<WorkspaceMember> findActiveMember(String workspaceId, String userId);
    /**
     * 更新指定成员的工作区角色。
     * <p>
     * 采用条件更新策略：仅当目标成员满足"活跃"条件时才执行 {@code UPDATE}，
     * 避免对已注销或已移除的成员误写。调用方通过返回值判断是否需要重试或报错。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param userId      用户 ID，不能为空
     * @param role        新角色枚举值，不能为空
     * @param updatedAt   更新时间戳，用于审计追踪
     * @return {@code true} 表示成功更新至少一行；{@code false} 表示未命中任何活跃成员     用户 ID
     * @param role        新角色
     * @param updatedAt   更新时间
     * @return 是否更新成功
     */
    boolean updateWorkspaceRole(String workspaceId, String userId, WorkspaceRole role, Instant updatedAt);
}
