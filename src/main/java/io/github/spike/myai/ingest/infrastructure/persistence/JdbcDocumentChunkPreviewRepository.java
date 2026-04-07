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
              COALESCE(metadata->>'sourceFile', '') AS source_file,
              COALESCE(metadata->>'contentHash', '') AS content_hash,
              COALESCE(metadata->>'splitVersion', '') AS split_version
            FROM vector_store
            WHERE metadata->>'documentId' = ?
            ORDER BY (metadata->>'chunkIndex')::int ASC
            LIMIT ?
            """;

    private static final RowMapper<DocumentChunkPreview> ROW_MAPPER = (rs, rowNum) -> new DocumentChunkPreview(
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getString("source_file"),
            rs.getString("content_hash"),
            rs.getString("split_version"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentChunkPreviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DocumentChunkPreview> findByDocumentId(DocumentId documentId, int limit) {
        return jdbcTemplate.query(FIND_BY_DOCUMENT_ID_SQL, ROW_MAPPER, documentId.value(), limit);
    }
}

