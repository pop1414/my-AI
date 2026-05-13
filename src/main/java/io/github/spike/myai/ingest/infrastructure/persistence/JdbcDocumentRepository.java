package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
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
               created_at, updated_at)
            VALUES (?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, CAST(? AS JSONB),
                    ?, ?)
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

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(Document document) {
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
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));
    }

    @Override
    public Optional<Document> findById(String workspaceId, DocumentId documentId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, DOCUMENT_ROW_MAPPER, workspaceId, documentId.value()).stream().findFirst();
    }

    @Override
    public Optional<Document> findByKbIdAndFileHash(String workspaceId, String kbId, String fileHash) {
        return jdbcTemplate.query(FIND_BY_KB_ID_AND_FILE_HASH_SQL, DOCUMENT_ROW_MAPPER, workspaceId, kbId, fileHash)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Document> findOldestReadyForProcessing(String workspaceId, Instant now) {
        return jdbcTemplate.query(FIND_OLDEST_READY_SQL, DOCUMENT_ROW_MAPPER, workspaceId, Timestamp.from(now))
                .stream()
                .findFirst();
    }

    @Override
    public boolean compareAndSetStatus(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            UploadStatus targetStatus,
            String failureReason,
            Instant updatedAt) {
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

    @Override
    public boolean markIndexed(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String processingMetadata,
            Instant updatedAt) {
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

    @Override
    public boolean requestReprocess(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String newSplitVersion,
            Instant requestedAt) {
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

    @Override
    public boolean markDeleting(String workspaceId, DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt) {
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

    @Override
    public boolean markDeleted(String workspaceId, DocumentId documentId, Instant updatedAt) {
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

    @Override
    public boolean rollbackDeleting(String workspaceId, DocumentId documentId, UploadStatus rollbackStatus, Instant updatedAt) {
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

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }
}
