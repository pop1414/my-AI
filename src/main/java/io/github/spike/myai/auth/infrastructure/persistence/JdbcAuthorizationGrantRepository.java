package io.github.spike.myai.auth.infrastructure.persistence;

import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 授权 grant JDBC 仓储实现。
 *
 * <p>实现 {@link AuthorizationGrantRepository} 端口，使用 {@link JdbcTemplate}
 * 对 PostgreSQL 的 {@code knowledge_base_grants} 和 {@code document_grants}
 * 表执行授权查询。
 *
 * <p>查询策略：
 * <ul>
 *   <li>仅读取 status = ACTIVE 的授权记录，软删除/禁用状态自动过滤；</li>
 *   <li>若存在多条匹配记录（如同一用户被多次授予不同角色），
 *       取第一条（由 {@code findFirst()} 决定），实际应保证数据唯一性；</li>
 *   <li>SQL 提取为常量（Text Block），避免字符串拼接，提升可维护性；</li>
 *   <li>查询结果通过 {@code Enum.valueOf()} 从数据库字符串映射为类型安全的枚举。</li>
 * </ul>
 *
 * <p>数据库结构由 Flyway 迁移脚本维护，本仓储不参与建表。
 *
 * @author spike
 * @since 1.0.0
 */
@Repository
public class JdbcAuthorizationGrantRepository implements AuthorizationGrantRepository {

    /**
     * 查询知识库角色的 SQL 模板。
     *
     * <p>条件：工作空间 + 知识库 + 用户 + 状态为 ACTIVE。
     * 返回 role 列（如 {@code KB_MANAGER}、{@code KB_READER} 等字符串）。
     */
    private static final String FIND_KNOWLEDGE_BASE_ROLE_SQL = """
            SELECT role
            FROM knowledge_base_grants
            WHERE workspace_id = ?
              AND kb_id = ?
              AND user_id = ?
              AND status = 'ACTIVE'
            """;

    /**
     * 查询文档权限的 SQL 模板。
     *
     * <p>条件：工作空间 + 文档 + 用户 + 状态为 ACTIVE。
     * 返回 permission 列（如 {@code DOC_DENY}、{@code DOC_ALLOW_READ} 等字符串）。
     */
    private static final String FIND_DOCUMENT_PERMISSION_SQL = """
            SELECT permission
            FROM document_grants
            WHERE workspace_id = ?
              AND document_id = ?
              AND user_id = ?
              AND status = 'ACTIVE'
            """;

    /**
     * 查询用户在当前工作区内具备 ACTIVE 知识库授权的全部知识库标识。
     */
    private static final String LIST_GRANTED_KNOWLEDGE_BASE_IDS_SQL = """
            SELECT kb_id
            FROM knowledge_base_grants
            WHERE workspace_id = ?
              AND user_id = ?
              AND status = 'ACTIVE'
            ORDER BY created_at ASC, kb_id ASC
            """;

    /** Spring JDBC 模板，用于执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcAuthorizationGrantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询用户在指定知识库上的 ACTIVE 授权角色。
     *
     * <p>执行流程：
     * <ol>
     *   <li>通过 {@code jdbcTemplate.queryForList} 执行参数化查询，返回 role 列的值列表；</li>
     *   <li>取第一条匹配记录（{@code findFirst()}）；</li>
     *   <li>将 role 字符串（如 {@code "KB_MANAGER"}）通过
     *       {@link KnowledgeBaseRole#valueOf} 映射为类型安全的枚举。</li>
     * </ol>
     *
     * @param workspaceId 工作空间标识
     * @param kbId        知识库标识
     * @param userId      用户标识
     * @return 有效知识库角色；无匹配记录时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<KnowledgeBaseRole> findKnowledgeBaseRole(String workspaceId, String kbId, String userId) {
        // 执行参数化查询，返回 role 列的字符串列表
        return jdbcTemplate.queryForList(FIND_KNOWLEDGE_BASE_ROLE_SQL, String.class, workspaceId, kbId, userId)
                .stream()
                // 取第一条匹配记录（数据应保证唯一性）
                .findFirst()
                // 字符串 → 枚举映射（如 "KB_MANAGER" → KnowledgeBaseRole.KB_MANAGER）
                .map(KnowledgeBaseRole::valueOf);
    }

    /**
     * 查询用户在指定文档上的 ACTIVE 权限覆盖。
     *
     * <p>执行流程：
     * <ol>
     *   <li>通过 {@code jdbcTemplate.queryForList} 执行参数化查询，返回 permission 列的值列表；</li>
     *   <li>取第一条匹配记录（{@code findFirst()}）；</li>
     *   <li>将 permission 字符串（如 {@code "DOC_DENY"}）通过
     *       {@link DocumentPermission#valueOf} 映射为类型安全的枚举。</li>
     * </ol>
     *
     * @param workspaceId 工作空间标识
     * @param documentId  文档标识
     * @param userId      用户标识
     * @return 有效文档权限；无匹配记录时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<DocumentPermission> findDocumentPermission(String workspaceId, String documentId, String userId) {
        // 执行参数化查询，返回 permission 列的字符串列表
        return jdbcTemplate.queryForList(FIND_DOCUMENT_PERMISSION_SQL, String.class, workspaceId, documentId, userId)
                .stream()
                // 取第一条匹配记录（数据应保证唯一性）
                .findFirst()
                // 字符串 → 枚举映射（如 "DOC_DENY" → DocumentPermission.DOC_DENY）
                .map(DocumentPermission::valueOf);
    }

    @Override
    public Set<String> listGrantedKnowledgeBaseIds(String workspaceId, String userId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                LIST_GRANTED_KNOWLEDGE_BASE_IDS_SQL,
                String.class,
                workspaceId,
                userId));
    }
}
