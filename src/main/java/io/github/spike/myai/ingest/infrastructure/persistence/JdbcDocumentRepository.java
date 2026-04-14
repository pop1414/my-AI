package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 文档仓储 PostgreSQL 实现（Infrastructure Adapter）。
 *
 * <p>设计说明：
 * <ul>
 *     <li>使用 JdbcTemplate 实现最小可读、可控的 SQL 访问。</li>
 *     <li>在仓储初始化时自动建表，降低本地开发门槛。</li>
 *     <li>采用 UPSERT（ON CONFLICT）保证 save 可用于新增和状态更新。</li>
 * </ul>
 */
@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    /**
     * 文档主表表名。
     */
    private static final String TABLE_NAME = "ingest_documents";

    /**
     * 基础建表 DDL。
     * 包含主键、知识库关联、文件元数据、状态机控制字段、重试机制字段以及版本控制字段。
     */
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS ingest_documents (
                document_id VARCHAR(64) PRIMARY KEY,
                kb_id VARCHAR(128) NOT NULL,
                file_hash VARCHAR(64),
                filename VARCHAR(512),
                file_size BIGINT NOT NULL,
                status VARCHAR(32) NOT NULL,
                failure_reason TEXT,
                retry_count INT NOT NULL DEFAULT 0,
                retry_max INT NOT NULL DEFAULT 3,
                next_retry_at TIMESTAMPTZ,
                last_error_code VARCHAR(64),
                last_error_message TEXT,
                last_error_at TIMESTAMPTZ,
                reprocess_count INT NOT NULL DEFAULT 0,
                reprocess_requested_at TIMESTAMPTZ,
                split_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                created_at TIMESTAMPTZ NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL
            );
            """;

    /**
     * 为文件内容哈希增加字段（用于去重和秒传判断）。
     */
    private static final String ADD_FILE_HASH_COLUMN_SQL = """
            ALTER TABLE ingest_documents
            ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64)
            """;

    /**
     * 兼容性升级：为老版本数据库补齐重试相关的持久化列。
     */
    private static final String ADD_RETRY_COLUMNS_SQL = """
            ALTER TABLE ingest_documents
            ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
            ADD COLUMN IF NOT EXISTS retry_max INT NOT NULL DEFAULT 3,
            ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(64),
            ADD COLUMN IF NOT EXISTS last_error_message TEXT,
            ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMPTZ
            """;

    /**
     * 兼容性升级：为老版本数据库补齐重处理计数及分块策略版本字段。
     */
    private static final String ADD_REPROCESS_COLUMNS_SQL = """
            ALTER TABLE ingest_documents
            ADD COLUMN IF NOT EXISTS reprocess_count INT NOT NULL DEFAULT 0,
            ADD COLUMN IF NOT EXISTS reprocess_requested_at TIMESTAMPTZ,
            ADD COLUMN IF NOT EXISTS split_version VARCHAR(32) NOT NULL DEFAULT 'v1'
            """;
    private static final String DROP_UNIQUE_INDEX_SQL = """
            DROP INDEX IF EXISTS uk_ingest_documents_kb_file_hash
            """;

    /**
     * 创建唯一索引。
     * 约束同一知识库（kb_id）下文件内容的唯一性，实现业务层面的上传幂等。
     */
    private static final String CREATE_UNIQUE_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
            ON ingest_documents (kb_id, file_hash)
            WHERE file_hash IS NOT NULL AND status <> 'DELETED'
            """;

    /**
     * UPSERT 逻辑：存在即更新，不存在即插入。
     * 使用 PostgreSQL 的 ON CONFLICT (document_id) 特性实现。
     */
    private static final String UPSERT_SQL = """
            INSERT INTO ingest_documents
              (document_id, kb_id, file_hash, filename, file_size, status, failure_reason,
               retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
               reprocess_count, reprocess_requested_at, split_version,
               created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?)
            ON CONFLICT (document_id) DO UPDATE SET
              kb_id = EXCLUDED.kb_id,
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
              created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at
            """;

    /**
     * 根据主键查询完整文档信息的 SQL。
     */
    private static final String FIND_BY_ID_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason,
                   retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
                   reprocess_count, reprocess_requested_at, split_version,
                   created_at, updated_at
            FROM ingest_documents
            WHERE document_id = ?
            """;

    /**
     * 实现“秒传”逻辑的查询语句。
     * 检查当前知识库内是否已存在相同 Hash 的文件，若存在则取最新的一条。
     */
    private static final String FIND_BY_KB_ID_AND_FILE_HASH_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason,
                   retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
                   reprocess_count, reprocess_requested_at, split_version,
                   created_at, updated_at
            FROM ingest_documents
            WHERE kb_id = ? AND file_hash = ? AND status <> 'DELETED'
            ORDER BY created_at DESC
            LIMIT 1
            """;

    /**
     * 核心调度查询：寻找最早达到可处理状态的任务。
     * 排除正在处理的任务、已达到最大重试次数的任务以及尚未到达重试退避时间的任务。
     */
    private static final String FIND_OLDEST_READY_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason,
                   retry_count, retry_max, next_retry_at, last_error_code, last_error_message, last_error_at,
                   reprocess_count, reprocess_requested_at, split_version,
                   created_at, updated_at
            FROM ingest_documents
            WHERE status = 'UPLOADED'
              AND (next_retry_at IS NULL OR next_retry_at <= ?)
              AND retry_count < retry_max
            ORDER BY COALESCE(next_retry_at, created_at) ASC, created_at ASC
            LIMIT 1
            """;

    /**
     * 通用的 CAS (Compare And Set) 状态更新 SQL。
     * 只有当数据库中的当前状态与 expectedStatus 一致时，才允许执行更新。
     */
    private static final String COMPARE_AND_SET_STATUS_SQL = """
            UPDATE ingest_documents
            SET status = ?, failure_reason = ?, updated_at = ?
            WHERE document_id = ? AND status = ?
            """;

    /**
     * 成功完成索引后的收口更新。
     * 除了将状态改为 INDEXED，还需要清空重试计数和错误上下文。
     */
    private static final String MARK_INDEXED_SQL = """
            UPDATE ingest_documents
            SET status = 'INDEXED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                last_error_at = NULL,
                updated_at = ?
            WHERE document_id = ? AND status = ?
            """;

    /**
     * 标记为彻底失败后的状态更新。
     * 永久性记录最后的错误码和详细错误报文。
     */
    private static final String MARK_FAILED_SQL = """
            UPDATE ingest_documents
            SET status = 'FAILED',
                failure_reason = ?,
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE document_id = ? AND status = ?
            """;

    /**
     * 瞬时错误触发重试时的状态更新。
     * 将状态重置为 UPLOADED 允许 worker 重新抢占，并设置下一次重试的具体时间点。
     */
    private static final String MARK_RETRY_SQL = """
            UPDATE ingest_documents
            SET status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = ?,
                next_retry_at = ?,
                last_error_code = ?,
                last_error_message = ?,
                last_error_at = ?,
                updated_at = ?
            WHERE document_id = ? AND status = ?
            """;

    /**
     * 标记文档重处理的 SQL。
     * 重置处理状态并在保留历史记录的同时递增重处理计数器。
     */
    private static final String REQUEST_REPROCESS_SQL = """
            UPDATE ingest_documents
            SET status = 'UPLOADED',
                failure_reason = NULL,
                retry_count = 0,
                next_retry_at = NULL,
                reprocess_count = reprocess_count + 1,
                reprocess_requested_at = ?,
                split_version = ?,
                updated_at = ?
            WHERE document_id = ? AND status = ?
            """;
    private static final String MARK_DELETING_SQL = """
            UPDATE ingest_documents
            SET status = 'DELETING',
                updated_at = ?
            WHERE document_id = ? AND status = ?
            """;
    private static final String MARK_DELETED_SQL = """
            UPDATE ingest_documents
            SET status = 'DELETED',
                updated_at = ?
            WHERE document_id = ? AND status = 'DELETING'
            """;
    private static final String ROLLBACK_DELETING_SQL = """
            UPDATE ingest_documents
            SET status = ?,
                updated_at = ?
            WHERE document_id = ? AND status = 'DELETING'
            """;

    /**
     * JDBC 结果集映射器：将数据库行原始数据装配回 Document 领域对象实例。
     */
    private static final RowMapper<Document> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new Document(
            new DocumentId(rs.getString("document_id")),
            rs.getString("kb_id"),
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
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数：注入 JdbcTemplate 并执行架构自愈（建表及字段补齐）。
     */
    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // 依次执行 DDL 语句以确保数据库 Schema 与代码模型版本同步。
        this.jdbcTemplate.execute(INIT_SQL);
        this.jdbcTemplate.execute(ADD_FILE_HASH_COLUMN_SQL);
        this.jdbcTemplate.execute(ADD_RETRY_COLUMNS_SQL);
        this.jdbcTemplate.execute(ADD_REPROCESS_COLUMNS_SQL);
        this.jdbcTemplate.execute(DROP_UNIQUE_INDEX_SQL);
        this.jdbcTemplate.execute(CREATE_UNIQUE_INDEX_SQL);
    }

    /**
     * 保存文档聚合（新增或更新）。
     *
     * @param document 领域文档对象
     */
    @Override
    public void save(Document document) {
        jdbcTemplate.update(
                UPSERT_SQL,
                document.documentId().value(),
                document.kbId(),
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
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));
    }

    /**
     * 根据文档 ID 查询文档聚合。
     *
     * @param documentId 文档 ID
     * @return 查询结果（Optional 封装）
     */
    @Override
    public Optional<Document> findById(DocumentId documentId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, DOCUMENT_ROW_MAPPER, documentId.value()).stream().findFirst();
    }

    /**
     * 根据知识库 ID 和文件哈希查找文档。
     * 用于在上传前检查是否存在完全一致且已处理的文件。
     */
    @Override
    public Optional<Document> findByKbIdAndFileHash(String kbId, String fileHash) {
        return jdbcTemplate.query(FIND_BY_KB_ID_AND_FILE_HASH_SQL, DOCUMENT_ROW_MAPPER, kbId, fileHash)
                .stream()
                .findFirst();
    }

    /**
     * 寻找最早的任务以进行异步处理。
     * 这是 InProcessWorker 等调度器的核心数据来源。
     */
    @Override
    public Optional<Document> findOldestReadyForProcessing(Instant now) {
        // 固定按 next_retry_at/created_at 升序取 1 条，保证处理顺序尽量稳定、可预期。
        return jdbcTemplate.query(FIND_OLDEST_READY_SQL, DOCUMENT_ROW_MAPPER, Timestamp.from(now))
                .stream()
                .findFirst();
    }

    /**
     * 原子性的状态变更方法。
     * 采用 CAS 机制防止多个处理进程/线程冲突导致的状态覆盖问题。
     */
    @Override
    public boolean compareAndSetStatus(
            DocumentId documentId,
            UploadStatus expectedStatus,
            UploadStatus targetStatus,
            String failureReason,
            Instant updatedAt) {
        // CAS 核心：where 子句里带 expectedStatus，只有状态匹配才更新成功。
        // 在并发场景下可避免后写覆盖先写导致的状态回退。
        int updatedRows = jdbcTemplate.update(
                COMPARE_AND_SET_STATUS_SQL,
                targetStatus.name(),
                failureReason,
                Timestamp.from(updatedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean markIndexed(DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt) {
        // 成功收口：重试信息一并清理，避免污染后续查询。
        int updatedRows = jdbcTemplate.update(
                MARK_INDEXED_SQL,
                Timestamp.from(updatedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean markFailed(
            DocumentId documentId,
            UploadStatus expectedStatus,
            String failureReason,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt) {
        // 失败收口：保留错误信息便于排障。
        int updatedRows = jdbcTemplate.update(
                MARK_FAILED_SQL,
                failureReason,
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean markRetry(
            DocumentId documentId,
            UploadStatus expectedStatus,
            int retryCount,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt) {
        // 瞬时失败：状态回到 UPLOADED，等待下一次抢占。
        int updatedRows = jdbcTemplate.update(
                MARK_RETRY_SQL,
                retryCount,
                toTimestamp(nextRetryAt),
                errorCode,
                errorMessage,
                toTimestamp(errorAt),
                Timestamp.from(updatedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean requestReprocess(
            DocumentId documentId,
            UploadStatus expectedStatus,
            String newSplitVersion,
            Instant requestedAt) {
        // 进入重处理队列：重置重试计数，并更新 splitVersion。
        int updatedRows = jdbcTemplate.update(
                REQUEST_REPROCESS_SQL,
                Timestamp.from(requestedAt),
                newSplitVersion,
                Timestamp.from(requestedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean markDeleting(DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt) {
        int updatedRows = jdbcTemplate.update(
                MARK_DELETING_SQL,
                Timestamp.from(updatedAt),
                documentId.value(),
                expectedStatus.name());
        return updatedRows == 1;
    }

    @Override
    public boolean markDeleted(DocumentId documentId, Instant updatedAt) {
        int updatedRows = jdbcTemplate.update(
                MARK_DELETED_SQL,
                Timestamp.from(updatedAt),
                documentId.value());
        return updatedRows == 1;
    }

    @Override
    public boolean rollbackDeleting(DocumentId documentId, UploadStatus rollbackStatus, Instant updatedAt) {
        int updatedRows = jdbcTemplate.update(
                ROLLBACK_DELETING_SQL,
                rollbackStatus.name(),
                Timestamp.from(updatedAt),
                documentId.value());
        return updatedRows == 1;
    }

    /**
     * 工具方法：将数据库的 Timestamp 类型安全转化为 Java 的 Instant 类型。
     */
    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    /**
     * 工具方法：将 Java 的 Instant 类型转化为数据库兼容的 Timestamp 类型（支持 null）。
     */
    private static Timestamp toTimestamp(Instant instant) {
        // 允许空值，便于 next_retry_at 等字段清空。
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }
}
