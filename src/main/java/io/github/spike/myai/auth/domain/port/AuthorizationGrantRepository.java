package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import java.util.Optional;

/**
 * 授权 grant 读取仓储端口（出站端口）。
 *
 * <p>定义授权判断所需的最小查询能力，位于六边形架构的领域层。
 * 该端口只暴露知识库角色查询和文档权限覆盖查询两个方法，
 * 避免应用层授权服务直接依赖 {@code knowledge_base_grants}
 * 与 {@code document_grants} 的表结构细节。
 *
 * <p>设计考量：
 * <ul>
 *   <li><strong>只读语义：</strong>授权数据由管理接口写入，
 *       此端口仅提供查询，不包含写操作；</li>
 *   <li><strong>状态过滤：</strong>实现层负责过滤 status = ACTIVE 的记录，
 *       应用层无需关心软删除/禁用状态；</li>
 *   <li><strong>单一返回：</strong>每个查询最多返回一条有效记录，
 *       若用户有多条授权（如被多次授予不同角色），实现层自行决定优先级策略。</li>
 * </ul>
 *
 * <p>当前唯一实现：
 * {@link io.github.spike.myai.auth.infrastructure.persistence.JdbcAuthorizationGrantRepository}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface AuthorizationGrantRepository {

    /**
     * 查询用户在指定知识库上的有效授权角色。
     *
     * <p>仅在知识库授权表中存在 status = ACTIVE 的记录时返回角色；
     * 否则返回空，表示该用户对此知识库无任何显式授权。
     *
     * @param workspaceId 工作空间标识
     * @param kbId        知识库标识
     * @param userId      用户标识
     * @return 有效知识库角色；无 ACTIVE 授权时返回 {@link Optional#empty()}
     */
    Optional<KnowledgeBaseRole> findKnowledgeBaseRole(String workspaceId, String kbId, String userId);

    /**
     * 查询用户在指定文档上的有效权限覆盖。
     *
     * <p>文档权限覆盖是对知识库角色的精细化补充：
     * <ul>
     *   <li>{@code DOC_DENY} —— 显式拒绝（最高优先级），覆盖所有知识库级角色；</li>
     *   <li>{@code DOC_ALLOW_READ} —— 显式允许读取（仅对 KB_ASKER 等低权限角色有意义）；</li>
     *   <li>{@code DOC_ALLOW_MANAGE} —— 显式允许管理。</li>
     * </ul>
     * 仅在文档授权表中存在 status = ACTIVE 的记录时返回权限。
     *
     * @param workspaceId 工作空间标识
     * @param documentId  文档标识
     * @param userId      用户标识
     * @return 有效文档权限；无 ACTIVE 覆盖时返回 {@link Optional#empty()}
     */
    Optional<DocumentPermission> findDocumentPermission(String workspaceId, String documentId, String userId);
}
