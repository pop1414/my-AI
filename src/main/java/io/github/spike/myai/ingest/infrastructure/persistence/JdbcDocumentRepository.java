package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档仓储 PostgreSQL 实现。
 *
 * <p>当前实现将稳定的 `document` 身份与 latest `document version`
 * 事实拆开存储，但对上层仍保持 `DocumentRepository` 端口不变。
 * 现阶段 `Document` 仍表示“文档资产 + latest version projection”。
 */
@Repository
@Transactional
public class JdbcDocumentRepository implements DocumentRepository {

    /**
     * 主表旧版本事实列仍做迁移兼容镜像写入。
     *
     * <p>{@code file_hash/filename/file_size/status/processing_metadata} 的真实事实源已经迁入
     * {@code ingest_document_versions}；这里保留写入仅服务旧索引、旧报表或灰度期人工排障，
     * 新生产读取路径不得再从这些旧列推导版本语义。
     */
    private static final String UPSERT_DOCUMENT_SQL = """
            INSERT INTO ingest_documents
              (document_id, workspace_id, kb_id, file_hash, filename, file_size, status,
               latest_version_number, latest_status, latest_filename, latest_version_origin_type,
               failure_reason, retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
               reprocess_count, reprocess_requested_at, split_version, processing_metadata,
               created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, CAST(? AS JSONB),
                    ?, ?)
            ON CONFLICT (document_id) DO UPDATE SET
              workspace_id = EXCLUDED.workspace_id,
              kb_id = EXCLUDED.kb_id,
              file_hash = EXCLUDED.file_hash,
              filename = EXCLUDED.filename,
              file_size = EXCLUDED.file_size,
              status = EXCLUDED.status,
              latest_version_number = EXCLUDED.latest_version_number,
              latest_status = EXCLUDED.latest_status,
              latest_filename = EXCLUDED.latest_filename,
              latest_version_origin_type = EXCLUDED.latest_version_origin_type,
              failure_reason = EXCLUDED.failure_reason,
              retry_count = EXCLUDED.retry_count,
              retry_max = EXCLUDED.retry_max,
              next_retry_at = EXCLUDED.next_retry_at,
              last_error_code = EXCLUDED.last_error_code,
              last_error_message = EXCLUDED.last_error_message,
              last_error_at = EXCLUDED.last_error_at,
              reprocess_count = EXCLUDED.reprocess_count,
              reprocess_requested_at = EXCLUDED.reprocess_requested_at,
              split_version = EXCLUDED.split_version,
              processing_metadata = EXCLUDED.processing_metadata,
              created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at
            """;

    private static final String UPSERT_DOCUMENT_VERSION_SQL = """
            INSERT INTO ingest_document_versions
              (document_id, version_number, version_origin_type, rollback_from_version_number,
               file_hash, filename, file_size, status, failure_reason,
               retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
               reprocess_count, reprocess_requested_at, split_version, processing_metadata,
               created_by_user_id, created_at, updated_at)
            VALUES (?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, CAST(? AS JSONB),
                    ?, ?, ?)
            ON CONFLICT (document_id, version_number) DO UPDATE SET
              version_origin_type = EXCLUDED.version_origin_type,
              rollback_from_version_number = EXCLUDED.rollback_from_version_number,
              file_hash = EXCLUDED.file_hash,
              filename = EXCLUDED.filename,
              file_size = EXCLUDED.file_size,
              status = EXCLUDED.status,
              failure_reason = EXCLUDED.failure_reason,
              retry_count = EXCLUDED.retry_count,
              retry_max = EXCLUDED.retry_max,
              next_retry_at = EXCLUDED.next_retry_at,
              last_error_code = EXCLUDED.last_error_code,
              last_error_message = EXCLUDED.last_error_message,
              last_error_at = EXCLUDED.last_error_at,
              reprocess_count = EXCLUDED.reprocess_count,
              reprocess_requested_at = EXCLUDED.reprocess_requested_at,
              split_version = EXCLUDED.split_version,
              processing_metadata = EXCLUDED.processing_metadata,
              created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at
            """;

    private static final String DOCUMENT_PROJECTION_SELECT = """
            SELECT d.document_id,
                   d.workspace_id,
                   d.kb_id,
                   d.latest_version_number,
                   d.latest_version_origin_type,
                   v.file_hash,
                   v.filename,
                   v.file_size,
                   v.status,
                   v.failure_reason,
                   v.retry_count,
                   v.retry_max,
                   v.next_retry_at,
                   v.last_error_code,
                   v.last_error_message,
                   v.last_error_at,
                   v.reprocess_count,
                   v.reprocess_requested_at,
                   v.split_version,
                   v.processing_metadata,
                   d.created_at,
                   d.updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions v
              ON v.document_id = d.document_id
             AND v.version_number = d.latest_version_number
            """;

    private static final String FIND_BY_ID_SQL = DOCUMENT_PROJECTION_SELECT + """
            WHERE d.workspace_id = ? AND d.document_id = ?
            """;

    private static final String FIND_VERSION_BY_NUMBER_SQL = """
            SELECT v.document_id,
                   v.version_number,
                   v.version_origin_type,
                   v.rollback_from_version_number,
                   v.file_hash,
                   v.filename,
                   v.file_size,
                   v.status,
                   v.failure_reason,
                   v.retry_count,
                   v.retry_max,
                   v.next_retry_at,
                   v.last_error_code,
                   v.last_error_message,
                   v.last_error_at,
                   v.reprocess_count,
                   v.reprocess_requested_at,
                   v.split_version,
                   v.processing_metadata,
                   v.created_by_user_id,
                   v.created_at,
                   v.updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions v
              ON v.document_id = d.document_id
            WHERE d.workspace_id = ?
              AND d.document_id = ?
              AND v.version_number = ?
            """;

    private static final String FIND_BY_KB_ID_AND_FILE_HASH_SQL = DOCUMENT_PROJECTION_SELECT + """
            WHERE d.workspace_id = ? AND d.kb_id = ? AND v.file_hash = ? AND d.latest_status <> 'DELETED'
            ORDER BY d.created_at DESC
            LIMIT 1
            """;

    private static final String FIND_OLDEST_READY_SQL = DOCUMENT_PROJECTION_SELECT + """
            WHERE d.workspace_id = ?
              AND d.latest_status = 'UPLOADED'
              AND (v.next_retry_at IS NULL OR v.next_retry_at <= ?)
              AND v.retry_count < v.retry_max
            ORDER BY COALESCE(v.next_retry_at, d.created_at) ASC, d.created_at ASC
            LIMIT 1
            """;

    /**
     * 以下主表状态/错误字段更新仅维护 latest projection 与旧兼容镜像。
     * 版本级处理事实必须同步写入 {@code ingest_document_versions}。
     */
    private static final String COMPARE_AND_SET_DOCUMENT_STATUS_SQL = """
            UPDATE ingest_documents
            SET status = ?, latest_status = ?, failure_reason = ?, updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String COMPARE_AND_SET_VERSION_STATUS_SQL = """
            UPDATE ingest_document_versions
            SET status = ?, failure_reason = ?, updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String MARK_INDEXED_DOCUMENT_SQL = """
            UPDATE ingest_documents
            SET status = 'INDEXED',
                latest_status = 'INDEXED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                last_error_at = NULL,
                processing_metadata = CAST(? AS JSONB),
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String MARK_INDEXED_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'INDEXED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                last_error_at = NULL,
                processing_metadata = CAST(? AS JSONB),
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String MARK_FAILED_DOCUMENT_SQL = """
            UPDATE ingest_documents
            SET status = 'FAILED',
                latest_status = 'FAILED',
                failure_reason = ?,
                processing_metadata = CAST(? AS JSONB),
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String MARK_FAILED_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'FAILED',
                failure_reason = ?,
                processing_metadata = CAST(? AS JSONB),
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String MARK_RETRY_DOCUMENT_SQL = """
            UPDATE ingest_documents
            SET status = 'UPLOADED',
                latest_status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = ?,
                next_retry_at = ?,
                processing_metadata = NULL,
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String MARK_RETRY_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = ?,
                next_retry_at = ?,
                processing_metadata = NULL,
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String REQUEST_REPROCESS_DOCUMENT_SQL = """
            UPDATE ingest_documents
            SET status = 'UPLOADED',
                latest_status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                processing_metadata = NULL,
                reprocess_count = reprocess_count + 1,
                reprocess_requested_at = ?,
                split_version = ?,
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String REQUEST_REPROCESS_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                processing_metadata = NULL,
                reprocess_count = reprocess_count + 1,
                reprocess_requested_at = ?,
                split_version = ?,
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String APPEND_UPLOAD_VERSION_DOCUMENT_SQL = """
            UPDATE ingest_documents
            SET file_hash = ?,
                filename = ?,
                file_size = ?,
                status = 'UPLOADED',
                latest_version_number = ?,
                latest_status = 'UPLOADED',
                latest_filename = ?,
                latest_version_origin_type = ?,
                failure_reason = NULL,
                retry_count = 0,
                retry_max = ?,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                last_error_at = NULL,
                reprocess_count = 0,
                reprocess_requested_at = NULL,
                split_version = ?,
                processing_metadata = NULL,
                updated_at = ?
            WHERE workspace_id = ?
              AND document_id = ?
              AND latest_version_number = ?
              AND latest_status IN ('INDEXED', 'FAILED')
            """;

    private static final String FIND_LATEST_INDEXED_VERSION_NUMBER_SQL = """
            SELECT COALESCE(MAX(v.version_number), 0)
            FROM ingest_documents d
            JOIN ingest_document_versions v
              ON v.document_id = d.document_id
            WHERE d.workspace_id = ?
              AND d.document_id = ?
              AND v.status = 'INDEXED'
            """;

    private static final String MARK_DELETING_SQL = """
            UPDATE ingest_documents
            SET status = 'DELETING',
                latest_status = 'DELETING',
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = ?
            """;

    private static final String MARK_DELETING_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'DELETING',
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String MARK_DELETED_SQL = """
            UPDATE ingest_documents
            SET status = 'DELETED',
                latest_status = 'DELETED',
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = 'DELETING'
            """;

    private static final String MARK_DELETED_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = 'DELETED',
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    private static final String ROLLBACK_DELETING_SQL = """
            UPDATE ingest_documents
            SET status = ?,
                latest_status = ?,
                updated_at = ?
            WHERE workspace_id = ? AND document_id = ? AND latest_status = 'DELETING'
            """;

    private static final String ROLLBACK_DELETING_VERSION_SQL = """
            UPDATE ingest_document_versions
            SET status = ?,
                updated_at = ?
            WHERE document_id = ?
              AND version_number = (
                  SELECT latest_version_number
                  FROM ingest_documents
                  WHERE workspace_id = ? AND document_id = ?
              )
            """;

    /**
     * 文档聚合行映射器：将 JOIN 查询结果集的一行组装为 {@link Document} 领域对象。
     *
     * <p>该映射器从 {@code DOCUMENT_PROJECTION_SELECT} 的投影列中读取字段，
     * 将数据库行转换为不可变的领域聚合。其中 {@code Timestamp} → {@code Instant}
     * 的转换通过 {@link #toInstant(Timestamp)} 完成，空值安全。
     */
    private static final RowMapper<Document> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new Document(
            new DocumentId(rs.getString("document_id")),
            rs.getString("workspace_id"),
            rs.getString("kb_id"),
            rs.getInt("latest_version_number"),
            DocumentVersionOriginType.valueOf(rs.getString("latest_version_origin_type")),
            rs.getString("file_hash"),
            rs.getString("filename"),
            rs.getLong("file_size"),
            UploadStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            rs.getInt("retry_count"),
            rs.getInt("retry_max"),
            toInstant(rs.getTimestamp("next_retry_at")),
            rs.getString("last_error_code"),
            rs.getString("last_error_message"),
            toInstant(rs.getTimestamp("last_error_at")),
            rs.getInt("reprocess_count"),
            toInstant(rs.getTimestamp("reprocess_requested_at")),
            rs.getString("split_version"),
            rs.getString("processing_metadata"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    /**
     * 文档版本事实映射器。
     *
     * <p>该映射器用于版本治理动作按版本号读取目标历史版本，
     * 字段来源仅限 {@code ingest_document_versions} 版本事实表。
     */
    private static final RowMapper<DocumentVersion> DOCUMENT_VERSION_ROW_MAPPER = (rs, rowNum) -> new DocumentVersion(
            new DocumentId(rs.getString("document_id")),
            rs.getInt("version_number"),
            DocumentVersionOriginType.valueOf(rs.getString("version_origin_type")),
            (Integer) rs.getObject("rollback_from_version_number"),
            rs.getString("file_hash"),
            rs.getString("filename"),
            rs.getLong("file_size"),
            UploadStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            rs.getInt("retry_count"),
            rs.getInt("retry_max"),
            toInstant(rs.getTimestamp("next_retry_at")),
            rs.getString("last_error_code"),
            rs.getString("last_error_message"),
            toInstant(rs.getTimestamp("last_error_at")),
            rs.getInt("reprocess_count"),
            toInstant(rs.getTimestamp("reprocess_requested_at")),
            rs.getString("split_version"),
            rs.getString("processing_metadata"),
            rs.getString("created_by_user_id"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    /** Spring JDBC 模板，用于执行所有数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring 配置的 JDBC 模板
     */
    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存文档聚合（INSERT ON CONFLICT UPDATE 语义）。
     *
     * <p>实现说明：
     * <ol>
     *   <li>主表 {@code ingest_documents}：写入/更新文档资产级属性及 latest 快照；</li>
     *   <li>版本表 {@code ingest_document_versions}：写入/更新 latest 版本事实；</li>
     *   <li>两表均使用 {@code INSERT ... ON CONFLICT DO UPDATE} 实现幂等写入。</li>
     * </ol>
     *
     * @param document 领域文档对象
     */
    @Override
    public void save(Document document) {
        save(document, null);
    }

    /**
     * 保存文档聚合并写入初始版本创建人。
     *
     * <p>{@code createdByUserId} 仅写入版本事实表的插入列；若发生版本行冲突，
     * 不覆盖既有创建人，避免兼容性更新误改审计事实。
     *
     * @param document 领域文档对象
     * @param createdByUserId 初始版本创建人用户 ID，可为空
     */
    @Override
    public void save(Document document, String createdByUserId) {
        // 写入主表：文档资产 + latest 版本快照投影
        jdbcTemplate.update(
                UPSERT_DOCUMENT_SQL,
                document.documentId().value(),
                document.workspaceId(),
                document.kbId(),
                document.fileHash(),
                document.filename(),
                document.fileSize(),
                document.status().name(),
                document.latestVersionNumber(),
                document.status().name(),
                document.filename(),
                document.latestVersionOriginType().name(),
                document.failureReason(),
                document.retryCount(),
                document.retryMax(),
                toTimestamp(document.nextRetryAt()),
                document.lastErrorCode(),
                document.lastErrorMessage(),
                toTimestamp(document.lastErrorAt()),
                document.reprocessCount(),
                toTimestamp(document.reprocessRequestedAt()),
                document.splitVersion(),
                document.processingMetadata(),
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));

        jdbcTemplate.update(
                UPSERT_DOCUMENT_VERSION_SQL,
                document.documentId().value(),
                document.latestVersionNumber(),
                document.latestVersionOriginType().name(),
                null,
                document.fileHash(),
                document.filename(),
                document.fileSize(),
                document.status().name(),
                document.failureReason(),
                document.retryCount(),
                document.retryMax(),
                toTimestamp(document.nextRetryAt()),
                document.lastErrorCode(),
                document.lastErrorMessage(),
                toTimestamp(document.lastErrorAt()),
                document.reprocessCount(),
                toTimestamp(document.reprocessRequestedAt()),
                document.splitVersion(),
                document.processingMetadata(),
                createdByUserId,
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));
    }

    /**
     * 按文档 ID 查询文档聚合。
     *
     * <p>通过 {@code DOCUMENT_PROJECTION_SELECT} 投影 JOIN 主表与版本表，
     * 返回 latest 版本的完整视图。
     *
     * @param workspaceId 工作区标识
     * @param documentId  文档 ID
     * @return 查询结果，未命中时返回空
     */
    @Override
    public Optional<Document> findById(String workspaceId, DocumentId documentId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, DOCUMENT_ROW_MAPPER, workspaceId, documentId.value()).stream().findFirst();
    }

    /**
     * 按版本号查询文档版本事实。
     *
     * <p>通过主表校验 workspace 边界，再读取版本表中的目标版本事实。
     *
     * @param workspaceId   工作区标识
     * @param documentId    文档 ID
     * @param versionNumber 版本号
     * @return 查询结果，未命中时返回空
     */
    @Override
    public Optional<DocumentVersion> findVersionByNumber(String workspaceId, DocumentId documentId, int versionNumber) {
        return jdbcTemplate.query(
                        FIND_VERSION_BY_NUMBER_SQL,
                        DOCUMENT_VERSION_ROW_MAPPER,
                        workspaceId,
                        documentId.value(),
                        versionNumber)
                .stream()
                .findFirst();
    }

    /**
     * 按知识库和文件哈希查询文档，用于上传受理幂等检测。
     *
     * <p>排除已删除（{@code DELETED}）状态的文档，按创建时间倒序取首条。
     *
     * @param workspaceId 工作区标识
     * @param kbId        知识库 ID
     * @param fileHash    文件内容哈希（SHA-256 十六进制）
     * @return 查询结果，未命中时返回空
     */
    @Override
    public Optional<Document> findByKbIdAndFileHash(String workspaceId, String kbId, String fileHash) {
        return jdbcTemplate.query(FIND_BY_KB_ID_AND_FILE_HASH_SQL, DOCUMENT_ROW_MAPPER, workspaceId, kbId, fileHash)
                .stream()
                .findFirst();
    }

    /**
     * 查找最早可处理的一条文档（用于异步调度轮询）。
     *
     * <p>筛选条件：
     * <ul>
     *   <li>latest_status = UPLOADED；</li>
     *   <li>next_retry_at 为空或已到期；</li>
     *   <li>retry_count &lt; retry_max；</li>
     *   <li>按 next_retry_at / created_at 升序，取首条。</li>
     * </ul>
     *
     * @param workspaceId 工作区标识
     * @param now         当前时间
     * @return 查询结果，未命中时返回空
     */
    @Override
    public Optional<Document> findOldestReadyForProcessing(String workspaceId, Instant now) {
        return jdbcTemplate.query(FIND_OLDEST_READY_SQL, DOCUMENT_ROW_MAPPER, workspaceId, Timestamp.from(now))
                .stream()
                .findFirst();
    }

    /**
     * 通用 CAS 状态更新：仅当当前状态与期望一致时才执行更新。
     *
     * <p>同时更新主表 {@code ingest_documents} 和版本表 {@code ingest_document_versions}
     * 的状态字段，确保两表状态一致。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望当前状态（CAS 条件）
     * @param targetStatus   目标状态
     * @param failureReason  失败原因（非失败状态可传 null）
     * @param updatedAt      更新时间
     * @return 更新是否成功（影响行数 == 1）
     */
    @Override
    public boolean compareAndSetStatus(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            UploadStatus targetStatus,
            String failureReason,
            Instant updatedAt) {
        // 先更新主表，成功再同步版本表
        int updatedRows = jdbcTemplate.update(
                COMPARE_AND_SET_DOCUMENT_STATUS_SQL,
                targetStatus.name(),
                targetStatus.name(),
                failureReason,
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                COMPARE_AND_SET_VERSION_STATUS_SQL,
                targetStatus.name(),
                failureReason,
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 标记处理成功（→ INDEXED），并回填处理元数据。
     *
     * <p>同时清理失败原因与重试信息，确保成功状态干净。
     * 主表和版本表同步更新，processingMetadata 通过 {@code CAST(? AS JSONB)} 写入。
     *
     * @param workspaceId        工作区标识
     * @param documentId         文档资产 ID
     * @param expectedStatus     期望状态（CAS 条件）
     * @param processingMetadata  处理结果元数据 JSON 字符串，可为空
     * @param updatedAt          更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean markIndexed(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String processingMetadata,
            Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                MARK_INDEXED_DOCUMENT_SQL,
                processingMetadata,
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                MARK_INDEXED_VERSION_SQL,
                processingMetadata,
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 标记处理失败（→ FAILED），记录完整错误信息。
     *
     * <p>用于「非瞬时错误」或「超过最大重试次数」的场景。
     * 同时写入 failureReason、errorCode、errorMessage 和 errorAt 供排障。
     *
     * @param workspaceId        工作区标识
     * @param documentId         文档资产 ID
     * @param expectedStatus     期望状态（CAS 条件）
     * @param failureReason      失败原因（已截断）
     * @param processingMetadata  处理结果元数据，可为空
     * @param errorCode          错误码
     * @param errorMessage       错误信息
     * @param errorAt            错误发生时间
     * @param updatedAt          更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean markFailed(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String failureReason,
            String processingMetadata,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                MARK_FAILED_DOCUMENT_SQL,
                failureReason,
                processingMetadata,
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                MARK_FAILED_VERSION_SQL,
                failureReason,
                processingMetadata,
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 标记瞬时失败并安排重试（状态回退到 UPLOADED）。
     *
     * <p>写入递增后的 retryCount 和指数退避计算出的 nextRetryAt，
     * 供调度器在到期后再次拾取处理。同时记录最后一次错误信息。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态（CAS 条件）
     * @param retryCount     最新重试次数
     * @param nextRetryAt    下一次重试时间
     * @param errorCode      错误码
     * @param errorMessage   错误信息
     * @param errorAt        错误发生时间
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean markRetry(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            int retryCount,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                MARK_RETRY_DOCUMENT_SQL,
                retryCount,
                toTimestamp(nextRetryAt),
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                MARK_RETRY_VERSION_SQL,
                retryCount,
                toTimestamp(nextRetryAt),
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 请求重处理：将状态回退到 UPLOADED，递增 reprocessCount 并更新 splitVersion。
     *
     * <p>用于 FAILED/INDEXED 状态的文档触发重新处理，
     * 清理失败/成功痕迹并分配新的分块版本号。
     *
     * @param workspaceId     工作区标识
     * @param documentId      文档资产 ID
     * @param expectedStatus  期望状态（FAILED/INDEXED）
     * @param newSplitVersion 新的分块版本号
     * @param requestedAt     请求时间
     * @return 更新是否成功
     */
    @Override
    public boolean requestReprocess(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String newSplitVersion,
            Instant requestedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                REQUEST_REPROCESS_DOCUMENT_SQL,
                Timestamp.from(requestedAt),
                newSplitVersion,
                Timestamp.from(requestedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                REQUEST_REPROCESS_VERSION_SQL,
                Timestamp.from(requestedAt),
                newSplitVersion,
                Timestamp.from(requestedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 追加新上传版本作为 latest 版本。
     *
     * <p>通过 CAS 校验 expectedLatestVersionNumber 与主表 latest_version_number
     * 一致后才执行更新，防止并发版本冲突。更新主表 latest 快照后，
     * 再通过 UPSERT 写入版本表的新版本事实行。
     *
     * @param workspaceId                 工作区标识
     * @param documentId                  文档资产 ID
     * @param expectedLatestVersionNumber 调用方期望的当前最新版本号
     * @param newVersion                  新版本事实
     * @param updatedAt                   更新时间
     * @return 是否成功追加
     */
    @Override
    public boolean appendUploadVersion(
            String workspaceId,
            DocumentId documentId,
            int expectedLatestVersionNumber,
            DocumentVersion newVersion,
            Instant updatedAt) {
        return appendVersion(workspaceId, documentId, expectedLatestVersionNumber, newVersion, updatedAt);
    }

    /**
     * 追加回退来源版本作为 latest 版本。
     *
     * <p>与上传新版本共用同一套 latest projection 更新逻辑，
     * 区别由 {@link DocumentVersion#versionOriginType()} 决定。
     *
     * @param workspaceId                 工作区标识
     * @param documentId                  文档资产 ID
     * @param expectedLatestVersionNumber 调用方期望的当前最新版本号
     * @param newVersion                  新版本事实
     * @param updatedAt                   更新时间
     * @return 是否成功追加
     */
    @Override
    public boolean appendRollbackVersion(
            String workspaceId,
            DocumentId documentId,
            int expectedLatestVersionNumber,
            DocumentVersion newVersion,
            Instant updatedAt) {
        return appendVersion(workspaceId, documentId, expectedLatestVersionNumber, newVersion, updatedAt);
    }

    /**
     * 追加版本并同步 latest projection。
     *
     * @param workspaceId                 工作区标识
     * @param documentId                  文档资产 ID
     * @param expectedLatestVersionNumber 调用方期望的当前最新版本号
     * @param newVersion                  新版本事实
     * @param updatedAt                   更新时间
     * @return 是否成功追加
     */
    private boolean appendVersion(
            String workspaceId,
            DocumentId documentId,
            int expectedLatestVersionNumber,
            DocumentVersion newVersion,
            Instant updatedAt) {
        // 先 CAS 更新主表 latest 快照
        int updatedRows = jdbcTemplate.update(
                APPEND_UPLOAD_VERSION_DOCUMENT_SQL,
                newVersion.fileHash(),
                newVersion.filename(),
                newVersion.fileSize(),
                newVersion.versionNumber(),
                newVersion.filename(),
                newVersion.versionOriginType().name(),
                newVersion.retryMax(),
                newVersion.splitVersion(),
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedLatestVersionNumber);
        if (updatedRows != 1) {
            return false;
        }

        jdbcTemplate.update(
                UPSERT_DOCUMENT_VERSION_SQL,
                newVersion.documentId().value(),
                newVersion.versionNumber(),
                newVersion.versionOriginType().name(),
                newVersion.rollbackFromVersionNumber(),
                newVersion.fileHash(),
                newVersion.filename(),
                newVersion.fileSize(),
                newVersion.status().name(),
                newVersion.failureReason(),
                newVersion.retryCount(),
                newVersion.retryMax(),
                toTimestamp(newVersion.nextRetryAt()),
                newVersion.lastErrorCode(),
                newVersion.lastErrorMessage(),
                toTimestamp(newVersion.lastErrorAt()),
                newVersion.reprocessCount(),
                toTimestamp(newVersion.reprocessRequestedAt()),
                newVersion.splitVersion(),
                newVersion.processingMetadata(),
                newVersion.createdByUserId(),
                Timestamp.from(newVersion.createdAt()),
                Timestamp.from(newVersion.updatedAt()));
        return true;
    }

    /**
     * 查询当前可问答版本号。
     *
     * <p>规则：同一 document 下版本号最大的 INDEXED 版本。
     * 使用 {@code COALESCE(MAX(v.version_number), 0)} 确保无结果时返回 0。
     *
     * @param workspaceId 工作区标识
     * @param documentId  文档资产 ID
     * @return 可问答版本号；不存在时返回 0
     */
    @Override
    public int findLatestIndexedVersionNumber(String workspaceId, DocumentId documentId) {
        Integer versionNumber = jdbcTemplate.queryForObject(
                FIND_LATEST_INDEXED_VERSION_NUMBER_SQL,
                Integer.class,
                workspaceId,
                documentId.value());
        return versionNumber == null ? 0 : versionNumber;
    }

    /**
     * 将文档状态推进为 DELETING（删除流程第一阶段）。
     *
     * <p>同时更新主表和版本表的状态。仅当当前状态与 expectedStatus
     * 一致时才执行，确保删除流程的原子启动。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态（通常为 FAILED/INDEXED）
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean markDeleting(String workspaceId, DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                MARK_DELETING_SQL,
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value(),
                expectedStatus.name());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                MARK_DELETING_VERSION_SQL,
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 将文档状态从 DELETING 推进为 DELETED（删除流程第二阶段）。
     *
     * <p>仅当文档当前状态为 DELETING 时才执行，确保删除流程的原子完成。
     * 同时更新版本表状态。
     *
     * @param workspaceId 工作区标识
     * @param documentId  文档资产 ID
     * @param updatedAt   更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean markDeleted(String workspaceId, DocumentId documentId, Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                MARK_DELETED_SQL,
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                MARK_DELETED_VERSION_SQL,
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 删除流程失败时回滚 DELETING 状态到原状态。
     *
     * <p>用于删除失败时的补偿操作（如向量删除成功但源文件清理失败），
     * 将文档恢复到进入删除前的可用状态。同时回滚版本表状态。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param rollbackStatus 回滚目标状态（进入删除前的状态）
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    @Override
    public boolean rollbackDeleting(String workspaceId, DocumentId documentId, UploadStatus rollbackStatus, Instant updatedAt) {
        // 先更新主表，CAS 不匹配时直接返回 false
        int updatedRows = jdbcTemplate.update(
                ROLLBACK_DELETING_SQL,
                rollbackStatus.name(),
                rollbackStatus.name(),
                Timestamp.from(updatedAt),
                workspaceId,
                documentId.value());
        if (updatedRows != 1) {
            return false;
        }
        jdbcTemplate.update(
                ROLLBACK_DELETING_VERSION_SQL,
                rollbackStatus.name(),
                Timestamp.from(updatedAt),
                documentId.value(),
                workspaceId,
                documentId.value());
        return true;
    }

    /**
     * 将 JDBC {@link Timestamp} 安全转换为 {@link Instant}。
     *
     * <p>数据库中的可空时间字段在 JDBC 层可能返回 null，
     * 此处做空值防护，避免 NPE。
     *
     * @param timestamp JDBC 时间戳，可为 null
     * @return 对应的 Instant，null 入参时返回 null
     */
    private static Instant toInstant(Timestamp timestamp) {
        // 空值安全：数据库可空时间字段返回 null 时直接透传
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    /**
     * 将 {@link Instant} 安全转换为 JDBC {@link Timestamp}。
     *
     * <p>领域模型中的可空时间字段为 null 时，JDBC 参数也需传 null，
     * 此处做空值防护。
     *
     * @param instant 领域时间对象，可为 null
     * @return 对应的 Timestamp，null 入参时返回 null
     */
    private static Timestamp toTimestamp(Instant instant) {
        // 空值安全：领域模型可空时间字段为 null 时直接透传
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }
}
