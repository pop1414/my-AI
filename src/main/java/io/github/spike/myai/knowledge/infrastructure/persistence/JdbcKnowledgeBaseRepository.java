package io.github.spike.myai.knowledge.infrastructure.persistence;

import io.github.spike.myai.ingest.infrastructure.persistence.JdbcDocumentRepository;
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
 *   <li><b>表结构自维护</b>：在构造阶段自动执行 DDL，确保所需表、索引、
 *       默认数据存在（适合开发/演示环境，生产环境建议使用 Flyway/Liquibase）；</li>
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
 *       {@code status = 'INDEXED'} 来实时计算已索引文档数；</li>
 *   <li>构造器中执行 Schema 初始化与数据回填，
 *       确保 {@code ingest_documents} 表中已有的 kb_id 在知识库表中有对应记录。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 * @see KnowledgeBaseRepository
 */
@Repository
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {

    // ======================== DDL / DML SQL 常量 ========================

    /**
     * 建表 DDL：{@code knowledge_bases} 表。
     *
     * <p>表结构说明：
     * <ul>
     *   <li>{@code id} —— 自增主键（内部使用，不对外暴露）；</li>
     *   <li>{@code kb_id} —— 业务键（对外唯一标识，有唯一索引）；</li>
     *   <li>{@code name} —— 名称（最长 100 字符）；</li>
     *   <li>{@code description} —— 描述（最长 500 字符，默认空字符串）；</li>
     *   <li>{@code status} —— 状态枚举字符串（默认 ACTIVE）；</li>
     *   <li>{@code created_at / updated_at} —— 审计时间戳（带时区）。</li>
     * </ul>
     */
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS knowledge_bases (
                id BIGSERIAL PRIMARY KEY,
                kb_id VARCHAR(64) NOT NULL,
                name VARCHAR(100) NOT NULL,
                description VARCHAR(500) NOT NULL DEFAULT '',
                status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                created_at TIMESTAMPTZ NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL
            )
            """;

    /** 为 {@code kb_id} 创建唯一索引，保证业务键全局唯一 */
    private static final String CREATE_UNIQUE_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_kb_id
            ON knowledge_bases (kb_id)
            """;

    /**
     * 插入默认知识库（如果尚不存在）。
     *
     * <p>{@code kb_id = 'default'} 作为系统兜底知识库，
     * 当文档未指定 kb_id 时默认归入此库。
     */
    private static final String INSERT_DEFAULT_KB_SQL = """
            INSERT INTO knowledge_bases (kb_id, name, description, status, created_at, updated_at)
            SELECT 'default', 'default', '', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM knowledge_bases WHERE kb_id = 'default'
            )
            """;

    /**
     * 回填 SQL：将 {@code ingest_documents} 表中出现过的 kb_id
     * 同步到 {@code knowledge_bases} 表。
     *
     * <p>适用场景：知识库表重建后，从文档表中恢复已有的知识库记录。
     * 使用 {@code SELECT DISTINCT kb_id} 去重，避免重复插入。
     */
    private static final String BACKFILL_DISTINCT_KB_SQL = """
            INSERT INTO knowledge_bases (kb_id, name, description, status, created_at, updated_at)
            SELECT source.kb_id, source.kb_id, '', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            FROM (
                SELECT DISTINCT kb_id
                FROM ingest_documents
                WHERE kb_id IS NOT NULL AND kb_id <> ''
            ) source
            WHERE NOT EXISTS (
                SELECT 1
                FROM knowledge_bases kb
                WHERE kb.kb_id = source.kb_id
            )
            """;

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
            INSERT INTO knowledge_bases (kb_id, name, description, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (kb_id) DO UPDATE SET
                name = EXCLUDED.name,
                description = EXCLUDED.description,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

    /** 按业务键查询单个知识库聚合根 */
    private static final String FIND_BY_KB_ID_SQL = """
            SELECT kb_id, name, description, status, created_at, updated_at
            FROM knowledge_bases
            WHERE kb_id = ?
            """;

    /**
     * 知识库列表查询 SQL（含聚合统计）。
     *
     * <p>核心逻辑：
     * <ol>
     *   <li>{@code LEFT JOIN ingest_documents} —— 关联文档表，
     *       未关联的知识库文档计数为 0；</li>
     *   <li>{@code doc.status = 'INDEXED'} —— 只统计已索引完成的文档；</li>
     *   <li>{@code COALESCE(COUNT(...), 0)} —— 无文档时计数为 0；</li>
     *   <li>{@code GROUP BY} —— 按知识库维度聚合；</li>
     *   <li>{@code ORDER BY created_at ASC, kb_id ASC} —— 按创建时间升序，
     *       保证列表顺序稳定。</li>
     * </ol>
     */
    private static final String LIST_KNOWLEDGE_BASES_SQL = """
            SELECT kb.kb_id,
                   kb.name,
                   kb.description,
                   kb.status,
                   COALESCE(COUNT(doc.document_id), 0) AS indexed_document_count
            FROM knowledge_bases kb
            LEFT JOIN ingest_documents doc
                   ON doc.kb_id = kb.kb_id
                  AND doc.status = 'INDEXED'
            GROUP BY kb.kb_id, kb.name, kb.description, kb.status
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
                    rs.getString("name"),
                    rs.getString("description"),
                    KnowledgeBaseStatus.valueOf(rs.getString("status")),
                    rs.getLong("indexed_document_count"));

    // ======================== 依赖与构造 ========================

    /** Spring JDBC 模板，用于执行所有数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 文档仓储（暂未直接使用，保留用于后续扩展如级联删除等）。
     *
     * <p>{@link SuppressWarnings("unused")} 注解表示该字段为预留依赖。
     */
    @SuppressWarnings("unused")
    private final JdbcDocumentRepository jdbcDocumentRepository;

    /**
     * 构造器注入。
     *
     * <p>构造阶段自动执行 Schema 初始化（幂等操作）：
     * <ol>
     *   <li>建表（{@code IF NOT EXISTS}，幂等）；</li>
     *   <li>创建唯一索引（{@code IF NOT EXISTS}，幂等）；</li>
     *   <li>插入默认知识库（幂等）；</li>
     *   <li>回填文档表中已存在的 kb_id（幂等）。</li>
     * </ol>
     *
     * <p>注意：生产环境建议将 DDL 迁移至 Flyway/Liquibase，
     * 此处内嵌 DDL 仅为简化开发与演示环境的启动流程。
     *
     * @param jdbcTemplate           Spring JDBC 模板
     * @param jdbcDocumentRepository 文档仓储（预留依赖）
     */
    public JdbcKnowledgeBaseRepository(
            JdbcTemplate jdbcTemplate,
            JdbcDocumentRepository jdbcDocumentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcDocumentRepository = jdbcDocumentRepository;
        // 按依赖顺序执行 Schema 初始化（均为幂等操作）
        this.jdbcTemplate.execute(INIT_SQL);               // 1. 建表
        this.jdbcTemplate.execute(CREATE_UNIQUE_INDEX_SQL); // 2. 唯一索引
        this.jdbcTemplate.execute(INSERT_DEFAULT_KB_SQL);  // 3. 默认知识库
        this.jdbcTemplate.execute(BACKFILL_DISTINCT_KB_SQL);// 4. 回填已有 kb_id
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
     *
     * @param kbId 知识库业务键
     * @return 包含聚合根的 {@link Optional}，不存在时为空
     */
    @Override
    public Optional<KnowledgeBase> findByKbId(String kbId) {
        // 执行查询，取 Stream 首元素（最多一条，因为 kb_id 有唯一索引）
        return jdbcTemplate.query(FIND_BY_KB_ID_SQL, KNOWLEDGE_BASE_ROW_MAPPER, kbId)
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
     *
     * @return 知识库摘要视图列表（可能为空列表）
     */
    @Override
    public List<KnowledgeBaseSummary> listKnowledgeBases() {
        // 直接执行聚合查询，RowMapper 负责将结果集映射为读模型
        return jdbcTemplate.query(LIST_KNOWLEDGE_BASES_SQL, KNOWLEDGE_BASE_SUMMARY_ROW_MAPPER);
    }
}
