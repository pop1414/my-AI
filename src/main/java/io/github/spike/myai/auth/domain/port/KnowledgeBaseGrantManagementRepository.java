package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 知识库授权治理仓储端口。
 * <p>
 * 定义治理接口所需的知识库授权查询、授予和回收持久化契约。
 * 所有查询方法仅返回状态为 {@code ACTIVE} 的授权记录，
 * 回收操作为软删除（状态变更为 {@code DISABLED}），保留审计追溯能力。
 * <p>
 * 实现类需保证：
 * <ul>
 *   <li>查询方法在无匹配记录时返回空列表或 {@link Optional#empty()}，不抛异常</li>
 *   <li>{@code saveGrant} 采用 Upsert 语义，存在则更新，不存在则插入</li>
 *   <li>{@code disableGrant} 返回 {@code boolean} 以区分成功与无匹配行</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public interface KnowledgeBaseGrantManagementRepository {

    /**
     * 查询指定知识库下所有活跃授权记录。
     * <p>
     * 结果按创建时间升序排列，保证多次调用顺序一致。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param kbId        知识库 ID，不能为空
     * @return 活跃授权列表，无授权记录时返回空列表（非 {@code null}）
     */
    List<KnowledgeBaseGrant> findActiveGrants(String workspaceId, String kbId);

    /**
     * 查询指定用户当前拥有的所有活跃知识库授权。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param userId 用户 ID，不能为空
     * @return 活跃授权列表，无授权记录时返回空列表（非 {@code null}）
     */
    List<KnowledgeBaseGrant> findActiveGrantsByUser(String workspaceId, String userId);

    /**
     * 查询单条活跃授权记录。
     * <p>
     * 同时匹配工作区、知识库、用户三个维度及 {@code ACTIVE} 状态。
     * 适用于授权更新或回收前的存在性校验场景。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param kbId        知识库 ID，不能为空
     * @param userId      用户 ID，不能为空
     * @return 活跃授权，不存在则返回 {@link Optional#empty()}
     */
    Optional<KnowledgeBaseGrant> findActiveGrant(String workspaceId, String kbId, String userId);

    /**
     * 授予或更新知识库授权（Upsert 语义）。
     * <p>
     * 若指定知识库与用户的授权记录已存在，则更新角色和状态为 {@code ACTIVE}；
     * 否则插入新记录。此方法不返回布尔值，调用方通过后续查询确认结果。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param kbId        知识库 ID，不能为空
     * @param userId      用户 ID，不能为空
     * @param role        知识库角色枚举值，不能为空
     * @param updatedAt   操作时间戳，用于审计追踪
     */
    void saveGrant(String workspaceId, String kbId, String userId, KnowledgeBaseRole role, Instant updatedAt);

    /**
     * 回收知识库授权（软删除）。
     * <p>
     * 将匹配的 {@code ACTIVE} 授权记录状态变更为 {@code DISABLED}，
     * 不执行物理删除，保留审计追溯能力。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param kbId        知识库 ID，不能为空
     * @param userId      用户 ID，不能为空
     * @param updatedAt   操作时间戳
     * @return {@code true} 成功禁用至少一行；{@code false} 未命中任何活跃授权
     */
    boolean disableGrant(String workspaceId, String kbId, String userId, Instant updatedAt);
}
