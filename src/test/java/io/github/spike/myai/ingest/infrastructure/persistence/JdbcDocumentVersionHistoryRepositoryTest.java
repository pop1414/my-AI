package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistory;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistoryItem;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcDocumentVersionHistoryRepositoryTest {

    @Test
    @DisplayName("findByDocumentId 应按工作区和文档过滤并返回版本历史")
    void findByDocumentId_shouldFilterAndReturnVersionHistory() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentVersionHistoryRepository repository = new JdbcDocumentVersionHistoryRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("workspace-a"), eq("doc-1")))
                .thenReturn(List.of());

        DocumentVersionHistory result = repository.findByDocumentId(
                "workspace-a",
                new DocumentId("doc-1"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq("workspace-a"),
                eq("doc-1"));
        assertEquals("doc-1", result.documentId().value());
        assertTrue(result.items().isEmpty());
        assertTrue(sqlCaptor.getValue().contains("JOIN ingest_document_versions v"));
        assertTrue(sqlCaptor.getValue().contains("d.workspace_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("d.document_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY v.version_number DESC"));
    }

    @Test
    @DisplayName("RowMapper 应映射 rollback 来源、失败原因、latest projection 和时间字段")
    void rowMapper_shouldMapVersionHistoryFields() throws Exception {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentVersionHistoryRepository repository = new JdbcDocumentVersionHistoryRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("workspace-a"), eq("doc-rollback")))
                .thenReturn(List.of());

        repository.findByDocumentId("workspace-a", new DocumentId("doc-rollback"));

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<RowMapper> rowMapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(
                any(String.class),
                rowMapperCaptor.capture(),
                eq("workspace-a"),
                eq("doc-rollback"));

        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(resultSet.getString("document_id")).thenReturn("doc-rollback");
        when(resultSet.getString("workspace_id")).thenReturn("workspace-a");
        when(resultSet.getString("kb_id")).thenReturn("kb-1");
        when(resultSet.getInt("latest_version_number")).thenReturn(3);
        when(resultSet.getInt("version_number")).thenReturn(2);
        when(resultSet.getString("version_origin_type")).thenReturn("ROLLBACK");
        when(resultSet.getObject("rollback_from_version_number")).thenReturn(1);
        when(resultSet.getString("filename")).thenReturn("rollback.pdf");
        when(resultSet.getLong("file_size")).thenReturn(512L);
        when(resultSet.getString("status")).thenReturn("FAILED");
        when(resultSet.getString("failure_reason")).thenReturn("parse failed");
        when(resultSet.getTimestamp("created_at"))
                .thenReturn(Timestamp.from(Instant.parse("2026-05-08T10:00:00Z")));
        when(resultSet.getTimestamp("updated_at"))
                .thenReturn(Timestamp.from(Instant.parse("2026-05-08T10:05:00Z")));

        @SuppressWarnings("unchecked")
        RowMapper<DocumentVersionHistoryItem> rowMapper = rowMapperCaptor.getValue();
        DocumentVersionHistoryItem item = rowMapper.mapRow(resultSet, 0);

        assertEquals("doc-rollback", item.documentId().value());
        assertEquals("workspace-a", item.workspaceId());
        assertEquals("kb-1", item.kbId());
        assertEquals(3, item.latestVersionNumber());
        assertEquals(2, item.versionNumber());
        assertEquals(DocumentVersionOriginType.ROLLBACK, item.versionOriginType());
        assertEquals(1, item.rollbackFromVersionNumber());
        assertEquals("rollback.pdf", item.filename());
        assertEquals(512L, item.fileSize());
        assertEquals(UploadStatus.FAILED, item.status());
        assertEquals("parse failed", item.failureReason());
        assertEquals(Instant.parse("2026-05-08T10:00:00Z"), item.createdAt());
        assertEquals(Instant.parse("2026-05-08T10:05:00Z"), item.updatedAt());
    }
}
