package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentListFilter;
import io.github.spike.myai.ingest.domain.model.DocumentListItem;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentListRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的文档列表查询仓储实现（基础设施层适配器）。
 *
 * <p>该类是 {@link DocumentListRepository} 端口接口的 JDBC 实现，
 * 位于六边形架构的<b>基础设施层</b>。
 * 列表读取只依赖 document 主表上稳定的 latest projection，以及 latest version
 * 对应版本行上的补充事实；它不应该重新回退到主表旧兼容镜像列，也不应该承担
 * latest projection maintenance 责任。
 *
 * <h3>核心策略</h3>
 * <ol>
 *   <li><b>动态 SQL 构建</b>：根据 {@link DocumentListFilter} 中各字段的
 *       {@code null} 状态，动态拼接 WHERE 条件，避免写死多条固定 SQL；</li>
 *   <li><b>查询+计数分离</b>：先执行分页数据查询，再执行 COUNT 计数查询，
 *       两次查询共享同一 WHERE 子句和参数列表，保证数据一致性；</li>
 *   <li><b>读模型投影</b>：SQL 层通过 {@code CASE WHEN} 直接处理
 *       {@code failure_reason} 的条件返回，避免应用层再做判断。</li>
 * </ol>
 *
 * <h3>SQL 注入防护</h3>
 * <p>所有查询参数均通过 JDBC {@code ?} 占位符传参，
 * 不存在字符串拼接用户输入的风险。
 *
 * @author Spike
 * @since 1.0.0
 * @see DocumentListRepository
 */
@Repository
public class JdbcDocumentListRepository implements DocumentListRepository {

    // ======================== SQL 常量 ========================

    /**
     * 数据查询基础 SQL（SELECT 子句 + FROM 子句）。
     *
     * <p>{@code failure_reason} 通过 {@code CASE WHEN} 仅在 FAILED 状态时返回，
     * 其他状态返回 {@code NULL}，避免前端展示无意义信息。
     * 这里显式使用 {@code latest_*} 列，文档化列表读模型对 latest projection seam
     * 的依赖；后续即使 latest projection maintenance 下沉到数据库 module，
     * 此处读契约也应保持稳定。
     */
    private static final String SELECT_BASE_SQL = """
            SELECT d.document_id,
                   d.workspace_id,
                   d.kb_id,
                   d.latest_version_number,
                   d.latest_version_origin_type,
                   d.latest_filename AS filename,
                   v.file_size,
                   d.latest_status AS status,
                   CASE WHEN d.latest_status = 'FAILED' THEN v.failure_reason ELSE NULL END AS failure_reason,
                   d.created_at,
                   d.updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions v
              ON v.document_id = d.document_id
             AND v.version_number = d.latest_version_number
            """;

    /**
     * 计数查询基础 SQL。
     *
     * <p>使用 {@code COUNT(1)} 而非 {@code COUNT(*)}，在 PostgreSQL 中二者性能等价，
     * {@code COUNT(1)} 语义更明确（统计行数而非列数）。
     */
    private static final String COUNT_BASE_SQL = """
            SELECT COUNT(1)
            FROM ingest_documents d
            """;

    /**
     * 排序 + 分页 SQL 片段。
     *
     * <p>按创建时间倒序排列，最新上传的文档排在最前；
     * 同时间戳时按 document_id 倒序作为次级排序，保证分页结果稳定。
     */
    private static final String ORDER_AND_PAGE_SQL = """
            ORDER BY created_at DESC, document_id DESC
            LIMIT ? OFFSET ?
            """;

    // ======================== RowMapper 定义 ========================

    /**
     * {@link DocumentListItem} 读模型的 JDBC 行映射器。
     *
     * <p>映射要点：
     * <ul>
     *   <li>{@code document_id} → {@link DocumentId} 值对象；</li>
     *   <li>{@code status} → {@link UploadStatus} 枚举（{@code valueOf} 转换）；</li>
     *   <li>{@code failure_reason} → 可由 SQL 的 {@code CASE WHEN} 保证仅 FAILED 时有值，
     *       此处直接映射；</li>
     *   <li>{@code created_at / updated_at} → {@link Instant}（通过
     *       {@link #toInstant(Timestamp)} 转换）。</li>
     * </ul>
     */
    private static final RowMapper<DocumentListItem> ROW_MAPPER = (rs, rowNum) -> new DocumentListItem(
            new DocumentId(rs.getString("document_id")),
            rs.getString("workspace_id"),
            rs.getString("kb_id"),
            rs.getInt("latest_version_number"),
            DocumentVersionOriginType.valueOf(rs.getString("latest_version_origin_type")),
            rs.getString("filename"),
            rs.getLong("file_size"),
            UploadStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    // ======================== 依赖与构造 ========================

    /** Spring JDBC 模板，用于执行所有数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcDocumentListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ======================== 仓储操作实现 ========================

    /**
     * 根据筛选条件查询文档分页列表。
     *
     * <p>执行流程：
     * <ol>
     *   <li>调用 {@link #buildWhereClause} 动态构建 WHERE 子句；</li>
     *   <li>将 WHERE 参数加上 limit/offset 后执行数据查询；</li>
     *   <li>使用相同的 WHERE 参数（不含分页参数）执行 COUNT 查询；</li>
     *   <li>封装为 {@link DocumentListPage} 返回。</li>
     * </ol>
     *
     * @param filter 筛选与分页条件
     * @return 分页查询结果
     */
    @Override
    public DocumentListPage findPage(DocumentListFilter filter) {
        // 1. 动态构建 WHERE 子句，同时收集参数列表
        List<Object> filterArgs = new ArrayList<>();
        String whereClause = buildWhereClause(filter, filterArgs);

        // 2. 数据查询参数 = 过滤参数 + 分页参数（LIMIT, OFFSET）
        List<Object> queryArgs = new ArrayList<>(filterArgs);
        queryArgs.add(filter.limit());
        queryArgs.add(filter.offset());

        // 3. 执行分页数据查询
        List<DocumentListItem> items = jdbcTemplate.query(
                SELECT_BASE_SQL + whereClause + ORDER_AND_PAGE_SQL,
                ROW_MAPPER,
                queryArgs.toArray());

        // 4. 执行 COUNT 查询（仅使用过滤参数，不含分页参数）
        Long total = jdbcTemplate.queryForObject(
                COUNT_BASE_SQL + whereClause,
                Long.class,
                filterArgs.toArray());

        // 5. 封装结果返回（total 为 null 时兜底为 0）
        return new DocumentListPage(
                items,
                total == null ? 0L : total,
                filter.limit(),
                filter.offset());
    }

    /**
     * 根据过滤条件动态构建 WHERE 子句。
     *
     * <p>构建策略（按优先级）：
     * <ol>     *   <li><b>workspaceId 必选过滤</b>：始终追加 {@code workspace_id = ?}，
     *       确保多工作区数据隔离（即使当前仅单工作区）；</li>     *   <li>{@code kbId} 不为 {@code null} → 追加 {@code kb_id = ?} 精确匹配；</li>
     *   <li>{@code status} 不为 {@code null} → 追加 {@code status = ?} 精确匹配；
     *       若 {@code status} 为 {@code null} 但 {@code excludeDeleted} 为
     *       {@code true} → 追加 {@code status <> 'DELETED'} 排除条件；</li>
     *   <li>{@code filename} 不为 {@code null} → 追加
     *       {@code COALESCE(filename, '') LIKE ?} 模糊匹配；</li>
     *   <li>所有条件通过 {@code AND} 连接。</li>
     * </ol>
     *
     * <p>安全说明：所有参数均通过 JDBC {@code ?} 占位符传递，
     * 不存在 SQL 注入风险。唯独 {@code status <> 'DELETED'} 中的
     * {@code 'DELETED'} 是硬编码的枚举字符串，非用户输入。
     *
     * @param filter 过滤条件
     * @param args   参数列表（输出参数，调用方传入空列表，此处追加）
     * @return WHERE 子句（不含前导空格），无条件时返回空字符串
     */
    private static String buildWhereClause(DocumentListFilter filter, List<Object> args) {
        List<String> conditions = new ArrayList<>();

        // 0. workspaceId 必选过滤：始终追加，确保多工作区数据隔离
        conditions.add("d.workspace_id = ?");
        args.add(filter.workspaceId());

        // 1. kbId 精确过滤
        if (filter.kbId() != null) {
            conditions.add("d.kb_id = ?");
            args.add(filter.kbId());
        }

        // 2. 状态过滤：显式传入 → 精确匹配；未传且需排除已删除 → 追加 <> 'DELETED'
        if (filter.status() != null) {
            conditions.add("d.latest_status = ?");
            args.add(filter.status().name());
        } else if (filter.excludeDeleted()) {
            // 默认排除已删除文档（非用户输入，硬编码安全）
            conditions.add("d.latest_status <> 'DELETED'");
        }

        // 3. 文件名模糊匹配
        //    使用 COALESCE 处理 NULL 文件名，LIKE 支持前后通配
        if (filter.filename() != null) {
            conditions.add("COALESCE(d.latest_filename, '') LIKE ?");
            args.add("%" + filter.filename() + "%");
        }

        // 4. 无条件时返回空字符串（SQL 拼接时无 WHERE 子句）
        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions) + System.lineSeparator();
    }

    /**
     * 将 JDBC {@link Timestamp} 转换为 {@link Instant}。
     *
     * <p>处理 {@code null} 输入：数据库字段理论上不可为 {@code null}
     * （有 NOT NULL 约束），但作为防御性编程，对 {@code null} 返回 {@code null}。
     *
     * @param timestamp JDBC 时间戳（可能为 {@code null}）
     * @return 对应的 Instant（可能为 {@code null}）
     */
    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
