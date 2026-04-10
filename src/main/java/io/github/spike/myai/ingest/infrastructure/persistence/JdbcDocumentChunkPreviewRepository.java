package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentChunkPreviewRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 PostgreSQL 的文档分块预览查询实现。
 */
@Repository
public class JdbcDocumentChunkPreviewRepository implements DocumentChunkPreviewRepository {

    private static final String FIND_BY_DOCUMENT_ID_SQL = """
            SELECT
              COALESCE((metadata->>'chunkIndex')::int, 0) AS chunk_index,
              COALESCE(content, '') AS content,
              COALESCE(LENGTH(content), 0) AS content_length,
              COALESCE(metadata->>'sourceFile', '') AS source_file,
              COALESCE(metadata->>'contentHash', '') AS content_hash,
              COALESCE(metadata->>'splitVersion', '') AS split_version,
              COALESCE(metadata->>'sourceHint', '') AS source_hint
            FROM vector_store
            WHERE metadata->>'documentId' = ?
              AND metadata->>'splitVersion' = ?
            -- chunkIndex 作为排序关键，保证预览顺序稳定
            ORDER BY (metadata->>'chunkIndex')::int ASC
            LIMIT ? OFFSET ?
            """;
    private static final String COUNT_BY_DOCUMENT_ID_SQL = """
            SELECT COUNT(1)
            FROM vector_store
            WHERE metadata->>'documentId' = ?
              AND metadata->>'splitVersion' = ?
            """;

    private static final RowMapper<DocumentChunkPreview> ROW_MAPPER = (rs, rowNum) -> new DocumentChunkPreview(
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getInt("content_length"),
            rs.getString("source_file"),
            rs.getString("content_hash"),
            rs.getString("split_version"),
            rs.getString("source_hint"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentChunkPreviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunkPreview> findByDocumentId(DocumentId documentId, String splitVersion, int limit, int offset) {
        // 以 documentId + splitVersion 过滤，避免混入历史版本分块。
        return jdbcTemplate.query(
                FIND_BY_DOCUMENT_ID_SQL,
                ROW_MAPPER,
                documentId.value(),
                splitVersion,
                limit,
                offset);
    }

    @Override
    public int countByDocumentId(DocumentId documentId, String splitVersion) {
        Integer count = jdbcTemplate.queryForObject(
                COUNT_BY_DOCUMENT_ID_SQL,
                Integer.class,
                documentId.value(),
                splitVersion);
        return count == null ? 0 : count;
    }
}
