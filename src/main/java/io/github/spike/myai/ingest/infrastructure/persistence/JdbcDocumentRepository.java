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

    private static final String TABLE_NAME = "ingest_documents";
    private static final String INIT_SQL = """
            CREATE TABLE IF NOT EXISTS ingest_documents (
                document_id VARCHAR(64) PRIMARY KEY,
                kb_id VARCHAR(128) NOT NULL,
                file_hash VARCHAR(64),
                filename VARCHAR(512),
                file_size BIGINT NOT NULL,
                status VARCHAR(32) NOT NULL,
                failure_reason TEXT,
                created_at TIMESTAMPTZ NOT NULL,
                updated_at TIMESTAMPTZ NOT NULL
            );
            """;
    private static final String ADD_FILE_HASH_COLUMN_SQL = """
            ALTER TABLE ingest_documents
            ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64)
            """;
    private static final String CREATE_UNIQUE_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
            ON ingest_documents (kb_id, file_hash)
            WHERE file_hash IS NOT NULL
            """;
    private static final String UPSERT_SQL = """
            INSERT INTO ingest_documents
              (document_id, kb_id, file_hash, filename, file_size, status, failure_reason, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (document_id) DO UPDATE SET
              kb_id = EXCLUDED.kb_id,
              file_hash = EXCLUDED.file_hash,
              filename = EXCLUDED.filename,
              file_size = EXCLUDED.file_size,
              status = EXCLUDED.status,
              failure_reason = EXCLUDED.failure_reason,
              created_at = EXCLUDED.created_at,
              updated_at = EXCLUDED.updated_at
            """;
    private static final String FIND_BY_ID_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason, created_at, updated_at
            FROM ingest_documents
            WHERE document_id = ?
            """;
    private static final String FIND_BY_KB_ID_AND_FILE_HASH_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason, created_at, updated_at
            FROM ingest_documents
            WHERE kb_id = ? AND file_hash = ?
            ORDER BY created_at DESC
            LIMIT 1
            """;
    private static final String FIND_OLDEST_BY_STATUS_SQL = """
            SELECT document_id, kb_id, file_hash, filename, file_size, status, failure_reason, created_at, updated_at
            FROM ingest_documents
            WHERE status = ?
            ORDER BY created_at ASC
            LIMIT 1
            """;
    private static final String COMPARE_AND_SET_STATUS_SQL = """
            UPDATE ingest_documents
            SET status = ?, failure_reason = ?, updated_at = ?
            WHERE document_id = ? AND status = ?
            """;

    // 结果映射器
    private static final RowMapper<Document> DOCUMENT_ROW_MAPPER = (rs, rowNum) -> new Document(
            new DocumentId(rs.getString("document_id")),
            rs.getString("kb_id"),
            rs.getString("file_hash"),
            rs.getString("filename"),
            rs.getLong("file_size"),
            UploadStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // 兼容“新库初始化 + 老库平滑升级”两种启动场景。
        this.jdbcTemplate.execute(INIT_SQL);
        this.jdbcTemplate.execute(ADD_FILE_HASH_COLUMN_SQL);
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
                Timestamp.from(document.createdAt()),
                Timestamp.from(document.updatedAt()));
    }

    /**
     * 根据文档 ID 查询文档聚合。
     *
     * @param documentId 文档 ID
     * @return 查询结果
     */
    @Override
    public Optional<Document> findById(DocumentId documentId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, DOCUMENT_ROW_MAPPER, documentId.value()).stream().findFirst();
    }

    @Override
    public Optional<Document> findByKbIdAndFileHash(String kbId, String fileHash) {
        return jdbcTemplate.query(FIND_BY_KB_ID_AND_FILE_HASH_SQL, DOCUMENT_ROW_MAPPER, kbId, fileHash)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Document> findOldestByStatus(UploadStatus status) {
        // 固定按 created_at 升序取 1 条，保证处理顺序尽量稳定、可预期。
        return jdbcTemplate.query(FIND_OLDEST_BY_STATUS_SQL, DOCUMENT_ROW_MAPPER, status.name())
                .stream()
                .findFirst();
    }

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

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }
}
