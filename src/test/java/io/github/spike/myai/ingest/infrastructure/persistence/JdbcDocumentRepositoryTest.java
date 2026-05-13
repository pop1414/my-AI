package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JdbcDocumentRepository 单元测试。
 */
class JdbcDocumentRepositoryTest {

    @Test
    @DisplayName("构造初始化不应再执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcDocumentRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("save 与 markIndexed 应同时维护 latest projection 与 version processing_metadata")
    void processingMetadataMethods_shouldIncludeJsonbColumn() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        Instant now = Instant.now();
        Document document = new Document(
                new DocumentId("doc-meta-1"),
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                "kb-1",
                "hash-meta-1",
                "demo.pdf",
                256L,
                UploadStatus.INDEXED,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                "{\"schema_version\":\"v1\"}",
                now,
                now);

        repository.save(document);
        when(jdbcTemplate.update(contains("SET status = 'INDEXED'"), any(), any(), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("doc-meta-1"), eq("INGESTING")))
                .thenReturn(1);
        repository.markIndexed(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                new DocumentId("doc-meta-1"),
                UploadStatus.INGESTING,
                "{\"schema_version\":\"v1\"}",
                now);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).update(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().get(0).contains("processing_metadata"));
        assertTrue(sqlCaptor.getAllValues().get(0).contains("CAST(? AS JSONB)"));
        assertTrue(sqlCaptor.getAllValues().get(0).contains("latest_version_number"));
        assertTrue(sqlCaptor.getAllValues().get(1).contains("ingest_document_versions"));
        assertTrue(sqlCaptor.getAllValues().get(2).contains("processing_metadata = CAST(? AS JSONB)"));
        assertTrue(sqlCaptor.getAllValues().get(3).contains("ingest_document_versions"));
    }

    @Test
    @DisplayName("findByKbIdAndFileHash 查询应包含 DELETED 过滤条件")
    void findByKbIdAndFileHash_shouldExcludeDeletedStatus() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-1"), eq("hash-1")))
                .thenReturn(List.of());

        repository.findByKbIdAndFileHash(WorkspaceConstants.DEFAULT_WORKSPACE_ID, "kb-1", "hash-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-1"), eq("hash-1"));
        assertTrue(sqlCaptor.getValue().contains("status <> 'DELETED'"));
        assertTrue(sqlCaptor.getValue().contains("workspace_id = ?"));
    }

    @Test
    @DisplayName("markDeleting 与 markDeleted/rollbackDeleting 应返回更新结果")
    void deleteStateMethods_shouldReflectUpdatedRows() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        DocumentId documentId = new DocumentId("doc-1");
        Instant now = Instant.now();

        when(jdbcTemplate.update(contains("SET status = 'DELETING'"), any(), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("doc-1"), eq("INDEXED")))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("SET status = 'DELETED'"), any(), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("doc-1")))
                .thenReturn(0);
        when(jdbcTemplate.update(
                        contains("WHERE workspace_id = ? AND document_id = ? AND latest_status = 'DELETING'"),
                        eq("FAILED"),
                        eq("FAILED"),
                        any(),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("doc-1")))
                .thenReturn(1);

        assertTrue(repository.markDeleting(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId, UploadStatus.INDEXED, now));
        assertFalse(repository.markDeleted(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId, now));
        assertTrue(repository.rollbackDeleting(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId, UploadStatus.FAILED, now));
    }
}
