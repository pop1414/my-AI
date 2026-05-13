package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistory;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistoryItem;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentVersionHistoryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 文档版本历史 JDBC 读仓储。
 *
 * <p>基于 Spring {@link JdbcTemplate} 实现，
 * 负责将 SQL 查询结果映射为领域模型 {@link DocumentVersionHistoryItem}。
 *
 * <p>设计要点：
 * <ul>
 *   <li>SQL 使用 Text Block（Java 15+）提高可读性；</li>
 *   <li>RowMapper 为静态不可变常量，线程安全且可复用；</li>
 *   <li>{@code failure_reason} 通过 CASE WHEN 在 SQL 层面过滤——
 *       仅 status='FAILED' 的记录才返回原因，其余返回 NULL。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Repository
public class JdbcDocumentVersionHistoryRepository implements DocumentVersionHistoryRepository {

    /**
     * 按文档 ID 查询版本历史的 SQL。
     *
     * <p>关联策略：
     * <ul>
     *   <li>以 ingest_documents 为主表，通过 document_id 关联版本表；</li>
     *   <li>workspace_id 作为租户隔离条件，避免跨工作区数据泄露；</li>
     *   <li>latest_version_number 取自主表，每条版本记录携带同一值
     *       （用于应用层判定 isLatestVersion）；</li>
     *   <li>failure_reason 使用 CASE WHEN 在数据库层过滤，
     *       减少应用层条件判断。</li>
     * </ul>
     */
    private static final String FIND_BY_DOCUMENT_ID_SQL = """
            SELECT d.document_id,
                   d.workspace_id,
                   d.kb_id,
                   d.latest_version_number,
                   v.version_number,
                   v.version_origin_type,
                   v.rollback_from_version_number,
                   v.filename,
                   v.file_size,
                   v.status,
                   CASE WHEN v.status = 'FAILED' THEN v.failure_reason ELSE NULL END AS failure_reason,
                   v.created_at,
                   v.updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions v
              ON v.document_id = d.document_id
            WHERE d.workspace_id = ?
              AND d.document_id = ?
            ORDER BY v.version_number DESC
            """;

    /**
     * 行映射器（RowMapper）：将 JDBC 结果集行转换为领域模型。
     *
     * <p>映射注意事项：
     * <ul>
     *   <li>{@code rollback_from_version_number} 可为 NULL（非回滚版本），
     *       使用 {@link java.sql.ResultSet#getObject(String)} 获取并强转为 Integer；</li>
     *   <li>{@code version_origin_type} / {@code status} 为数据库 VARCHAR 列，
     *       通过 {@link Enum#valueOf(String)} 转换为强类型枚举；</li>
     *   <li>{@code created_at} / {@code updated_at} 为 {@link Timestamp} 类型，
     *       通过 {@link #toInstant(Timestamp)} 转为 {@link Instant}。</li>
     * </ul>
     */
    private static final RowMapper<DocumentVersionHistoryItem> ROW_MAPPER = (rs, rowNum) ->
            new DocumentVersionHistoryItem(
                    new DocumentId(rs.getString("document_id")),
                    rs.getString("workspace_id"),
                    rs.getString("kb_id"),
                    rs.getInt("latest_version_number"),
                    rs.getInt("version_number"),
                    // 数据库 VARCHAR → 枚举值映射
                    DocumentVersionOriginType.valueOf(rs.getString("version_origin_type")),
                    // rollback_from_version_number 可为 null（非回滚版本）
                    (Integer) rs.getObject("rollback_from_version_number"),
                    rs.getString("filename"),
                    rs.getLong("file_size"),
                    UploadStatus.valueOf(rs.getString("status")),
                    // failure_reason 已由 SQL CASE WHEN 处理，非 FAILED 时为 null
                    rs.getString("failure_reason"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("updated_at")));

    /** Spring JdbcTemplate：执行 SQL 查询的核心工具 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数（Spring 构造器注入）。
     *
     * @param jdbcTemplate Spring 自动配置的 JdbcTemplate 实例
     */
    public JdbcDocumentVersionHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按工作区与文档 ID 查询版本历史。
     *
     * <p>执行逻辑：
     * <ol>
     *   <li>使用预编译 SQL（参数化查询）防止 SQL 注入；</li>
     *   <li>workspaceId 作为第一参数，documentId 作为第二参数；</li>
     *   <li>RowMapper 将每行结果映射为领域模型；</li>
     *   <li>JdbcTemplate 自动关闭资源，无需手动释放连接。</li>
     * </ol>
     *
     * @param workspaceId 工作区标识（租户隔离）
     * @param documentId  文档资产 ID
     * @return 版本历史读模型（保证非 null）
     */
    @Override
    public DocumentVersionHistory findByDocumentId(
            String workspaceId,
            DocumentId documentId) {
        // 委托 JdbcTemplate 执行参数化查询，自动完成结果集映射
        List<DocumentVersionHistoryItem> items =
                jdbcTemplate.query(FIND_BY_DOCUMENT_ID_SQL, ROW_MAPPER, workspaceId, documentId.value());
        return new DocumentVersionHistory(documentId, items);
    }

    /**
     * 将 {@link Timestamp} 转换为 {@link Instant}。
     *
     * <p>该工具方法处理 JDBC 层返回的可能为 null 的时间戳，
     * 避免 NPE 并统一领域层时间类型。
     *
     * @param timestamp JDBC 返回的时间戳，可能为 null
     * @return 对应的 Instant 对象，当输入为 null 时返回 null
     */
    private static Instant toInstant(Timestamp timestamp) {
        // 防御性 null 检查：JDBC 返回的时间戳在允许为 NULL 的列上可能为 null
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
