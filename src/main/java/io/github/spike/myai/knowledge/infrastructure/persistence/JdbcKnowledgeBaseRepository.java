package io.github.spike.myai.knowledge.infrastructure.persistence;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 知识库主数据 JDBC 仓储实现（基础设施层适配器）。
 *
 * <p>该类是 {@link KnowledgeBaseRepository} 端口接口的 JDBC 实现，
 * 位于六边形架构的<b>基础设施层</b>，负责：
 * <ol>
 *   <li><b>CRUD 操作</b>：将领域聚合根映射到 {@code knowledge_bases} 表；</li>
 *   <li><b>读模型投影</b>：通过 LEFT JOIN + COUNT 聚合查询生成
 *       {@link KnowledgeBaseSummary} 视图。</li>
 * </ol>
 *
 * <h3>关键设计决策</h3>
 * <ul>
 *   <li>保存采用 {@code INSERT ... ON CONFLICT DO UPDATE}（UPSERT），
 *       避免先查后写的并发窗口；</li>
 *   <li>列表查询通过 {@code LEFT JOIN ingest_documents} 并过滤
 *       {@code latest_status = 'INDEXED'} 来实时计算已索引文档数；</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 * @see KnowledgeBaseRepository
 */
@Repository
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {

    /**
     * UPSERT SQL：保存聚合根的核心 SQL。
     *
     * <p>使用 PostgreSQL 的 {@code ON CONFLICT (kb_id) DO UPDATE} 语法：
     * <ul>
     *   <li>若 {@code kb_id} 不存在则插入新记录；</li>
     *   <li>若已存在则更新 {@code name / description / status / updated_at}，
     *       {@code id} 和 {@code created_at} 保持不变。</li>
     * </ul>
     */
    private static final String UPSERT_SQL = """
            INSERT INTO knowledge_bases (kb_id, workspace_id, name, description, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (kb_id) DO UPDATE SET
                workspace_id = EXCLUDED.workspace_id,
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

    /** 按业务键查询单个知识库聚合根 */
    private static final String FIND_BY_KB_ID_SQL = """
            SELECT kb_id, workspace_id, name, description, status, created_at, updated_at
            FROM knowledge_bases
            WHERE workspace_id = ? AND kb_id = ? AND status <> 'DELETED'
            """;

    /** 按业务键查询单个知识库聚合根，包含已软删除记录 */
    private static final String FIND_BY_KB_ID_INCLUDING_DELETED_SQL = """
            SELECT kb_id, workspace_id, name, description, status, created_at, updated_at
            FROM knowledge_bases
            WHERE workspace_id = ? AND kb_id = ?
            """;

    /**
     * 知识库列表查询 SQL（含聚合统计）。
     *
     * <p>核心逻辑：
     * <ol>
     *   <li>{@code LEFT JOIN ingest_documents} —— 关联文档表，
     *       未关联的知识库文档计数为 0；</li>
     *   <li>{@code doc.latest_status = 'INDEXED'} —— 只统计当前 latest projection 已索引完成的文档；</li>
     *   <li>{@code COALESCE(COUNT(...), 0)} —— 无文档时计数为 0；</li>
     *   <li>{@code GROUP BY} —— 按知识库维度聚合；</li>
     *   <li>{@code ORDER BY created_at ASC, kb_id ASC} —— 按创建时间升序，
     *       保证列表顺序稳定。</li>
     * </ol>
     */
    private static final String LIST_KNOWLEDGE_BASES_SQL = """
            SELECT kb.kb_id,
                   kb.workspace_id,
                   kb.name,
                   kb.description,
                   kb.status,
                   COALESCE(COUNT(doc.document_id), 0) AS indexed_document_count
            FROM knowledge_bases kb
            LEFT JOIN ingest_documents doc
                   ON doc.kb_id = kb.kb_id
                  AND doc.workspace_id = kb.workspace_id
                  AND doc.latest_status = 'INDEXED'
            WHERE kb.workspace_id = ?
              AND kb.status <> 'DELETED'
            GROUP BY kb.kb_id, kb.workspace_id, kb.name, kb.description, kb.status, kb.created_at
            ORDER BY kb.created_at ASC, kb.kb_id ASC
            """;

    // ======================== RowMapper 定义 ========================

    /**
     * {@link KnowledgeBase} 聚合根的 JDBC 行映射器。
     *
     * <p>将数据库字段映射为领域聚合根：
     * <ul>
     *   <li>{@code status} 列通过 {@link KnowledgeBaseStatus#valueOf} 还原为枚举；</li>
     *   <li>{@code created_at / updated_at} 通过 {@link java.sql.Timestamp#toInstant()}
     *       转换为 {@link Instant}。</li>
     * </ul>
     */
    private static final RowMapper<KnowledgeBase> KNOWLEDGE_BASE_ROW_MAPPER = (rs, rowNum) -> new KnowledgeBase(
            rs.getString("kb_id"),
            rs.getString("workspace_id"),
            rs.getString("name"),
            rs.getString("description"),
            KnowledgeBaseStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    /**
     * {@link KnowledgeBaseSummary} 读模型的 JDBC 行映射器。
     *
     * <p>与聚合根的映射器相比，额外处理 {@code indexed_document_count} 聚合字段。
     */
    private static final RowMapper<KnowledgeBaseSummary> KNOWLEDGE_BASE_SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new KnowledgeBaseSummary(
                    rs.getString("kb_id"),
                    rs.getString("workspace_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    KnowledgeBaseStatus.valueOf(rs.getString("status")),
                    rs.getLong("indexed_document_count"));

    // ======================== 依赖与构造 ========================

    /** Spring JDBC 模板，用于执行所有数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * <p>表结构由 Flyway 统一维护，仓储仅负责运行时读写。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcKnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 仓储操作实现 ========================

    /**
     * 保存知识库聚合根（UPSERT 语义）。
     *
     * <p>通过 PostgreSQL 的 {@code ON CONFLICT} 机制实现原子 UPSERT，
     * 避免"先查后写"可能导致的竞态条件。
     *
     * <p>字段映射：
     * <ul>
     *   <li>{@code status} —— 枚举通过 {@code name()} 转为字符串存储；</li>
     *   <li>{@code createdAt / updatedAt} —— {@link Instant} 通过
     *       {@link Timestamp#from} 转换为 JDBC 时间戳。</li>
     * </ul>
     *
     * @param knowledgeBase 待持久化的知识库聚合根
     */
    @Override
    public void save(KnowledgeBase knowledgeBase) {
        jdbcTemplate.update(
                UPSERT_SQL,
                knowledgeBase.kbId(),                           // 业务键
                knowledgeBase.workspaceId(),                    // 工作区
                knowledgeBase.name(),                           // 名称
                knowledgeBase.description(),                    // 描述
                knowledgeBase.status().name(),                  // 枚举 → 字符串
                Timestamp.from(knowledgeBase.createdAt()),      // 创建时间（入库时保持原值）
                Timestamp.from(knowledgeBase.updatedAt()));     // 更新时间
    }

    /**
     * 按业务键查询知识库聚合根。
     *
     * <p>使用 JDBC 查询后通过 Stream 取首条结果，
     * 若无匹配行则返回 {@link Optional#empty()}。
     * 查询时同时匹配 {@code workspace_id} 与 {@code kb_id}。
     *
     * @param workspaceId 工作区标识
     * @param kbId        知识库业务键
     * @return 包含聚合根的 {@link Optional}，不存在时为空
     */
    @Override
    public Optional<KnowledgeBase> findByKbId(String workspaceId, String kbId) {
        // 执行查询，取 Stream 首元素（最多一条，因为 kb_id 有唯一索引）
        return jdbcTemplate.query(FIND_BY_KB_ID_SQL, KNOWLEDGE_BASE_ROW_MAPPER, workspaceId, kbId)
                .stream()
                .findFirst();
    }

    /**
     * 按业务键查询知识库聚合根，包含已软删除记录。
     *
     * @param workspaceId 工作区标识
     * @param kbId        知识库业务键
     * @return 包含聚合根的 {@link Optional}，不存在时为空
     */
    @Override
    public Optional<KnowledgeBase> findByKbIdIncludingDeleted(String workspaceId, String kbId) {
        return jdbcTemplate.query(FIND_BY_KB_ID_INCLUDING_DELETED_SQL, KNOWLEDGE_BASE_ROW_MAPPER, workspaceId, kbId)
                .stream()
                .findFirst();
    }

    /**
     * 查询全量知识库摘要视图（含已索引文档数聚合统计）。
     *
     * <p>使用 {@code LEFT JOIN + COUNT + GROUP BY} 实时计算每个知识库的
     * 已索引文档数量。无文档的知识库计数为 0。
     *
     * <p>结果按创建时间升序排列，保证分页或增量同步时顺序稳定。
     * 查询限定在指定工作区范围内。
     *
     * @param workspaceId 工作区标识
     * @return 知识库摘要视图列表（可能为空列表）
     */
    @Override
    public List<KnowledgeBaseSummary> listKnowledgeBases(String workspaceId) {
        // 直接执行聚合查询，RowMapper 负责将结果集映射为读模型
        return jdbcTemplate.query(LIST_KNOWLEDGE_BASES_SQL, KNOWLEDGE_BASE_SUMMARY_ROW_MAPPER, workspaceId);
    }
}
