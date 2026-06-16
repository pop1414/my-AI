package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JdbcDocumentRepository 单元测试。
 */
/**
 * TODO(spike): Refactor to integration test
 *
 * Current implementation violates project rule:
 * "Do not mock JdbcTemplate/JDBC chain - SQL correctness must be verified via real database"
 *
 * Refactoring plan:
 * 1. Use Testcontainers for real PostgreSQL environment
 * 2. Remove JdbcTemplate mocks
 * 3. Verify SQL correctness via real database
 *
 * @see docs/project-context.md:187-188
 */
@Disabled("TODO: Refactor to integration test - remove JdbcTemplate mock")
class JdbcDocumentRepositoryTest {

    @Test
    @DisplayName("构造初始化不应再执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcDocumentRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("save 与 markIndexed 应通过 latest projection function 维护 processing_metadata")
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
        when(jdbcTemplate.queryForObject(
                        contains("SELECT ingest_update_latest_document_version_processing"),
                        eq(Boolean.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("doc-meta-1"),
                        eq("INGESTING"),
                        eq("INDEXED"),
                        isNull(),
                        eq(0),
                        isNull(),
                        eq("{\"schema_version\":\"v1\"}"),
                        isNull(),
                        isNull(),
                        isNull(),
                        any()))
                .thenReturn(Boolean.TRUE);
        repository.markIndexed(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                new DocumentId("doc-meta-1"),
                UploadStatus.INGESTING,
                "{\"schema_version\":\"v1\"}",
                now);

        ArgumentCaptor<String> upsertSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> functionSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(upsertSqlCaptor.capture(), any(Object[].class));
        verify(jdbcTemplate).queryForObject(functionSqlCaptor.capture(), eq(Boolean.class), any(Object[].class));
        assertTrue(upsertSqlCaptor.getAllValues().get(0).contains("processing_metadata"));
        assertTrue(upsertSqlCaptor.getAllValues().get(0).contains("CAST(? AS JSONB)"));
        assertTrue(upsertSqlCaptor.getAllValues().get(0).contains("latest_version_number"));
        assertTrue(upsertSqlCaptor.getAllValues().get(1).contains("ingest_document_versions"));
        assertTrue(upsertSqlCaptor.getAllValues().get(1).contains("created_by_user_id"));
        assertTrue(functionSqlCaptor.getValue().contains("ingest_update_latest_document_version_processing"));
        assertTrue(functionSqlCaptor.getValue().contains("CAST(? AS JSONB)"));
    }

    @Test
    @DisplayName("findByKbIdAndFileHash 应从 version 表读取文件哈希且仅排除已删除文档")
    void findByKbIdAndFileHash_shouldReadFileHashFromVersionFacts() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-1"), eq("hash-1")))
                .thenReturn(List.of());

        repository.findByKbIdAndFileHash(WorkspaceConstants.DEFAULT_WORKSPACE_ID, "kb-1", "hash-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-1"), eq("hash-1"));
        assertTrue(sqlCaptor.getValue().contains("JOIN ingest_document_versions v"));
        assertTrue(sqlCaptor.getValue().contains("v.file_hash = ?"));
        assertTrue(sqlCaptor.getValue().contains("d.latest_status <> 'DELETED'"));
        assertFalse(sqlCaptor.getValue().contains("DELETING"));
        assertFalse(sqlCaptor.getValue().contains("d.file_hash = ?"));
        assertFalse(sqlCaptor.getValue().contains("d.status <> 'DELETED'"));
        assertTrue(sqlCaptor.getValue().contains("d.workspace_id = ?"));
    }

    @Test
    @DisplayName("findVersionByNumber 应通过 workspace 与版本号读取版本事实")
    void findVersionByNumber_shouldReadTargetVersionFactsWithinWorkspace() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("doc-1"), eq(2)))
                .thenReturn(List.of());

        repository.findVersionByNumber(WorkspaceConstants.DEFAULT_WORKSPACE_ID, new DocumentId("doc-1"), 2);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                eq("doc-1"),
                eq(2));
        assertTrue(sqlCaptor.getValue().contains("JOIN ingest_document_versions v"));
        assertTrue(sqlCaptor.getValue().contains("d.workspace_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("v.version_number = ?"));
    }

    @Test
    @DisplayName("appendRollbackVersion 与 markFailed 应收口到 latest projection function")
    void appendRollbackVersionAndMarkFailed_shouldKeepLatestVersionProjection() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        DocumentId documentId = new DocumentId("doc-rollback");
        Instant now = Instant.now();
        DocumentVersion rollbackVersion = new DocumentVersion(
                documentId,
                4,
                DocumentVersionOriginType.ROLLBACK,
                1,
                "hash-v1",
                "v1.pdf",
                128L,
                UploadStatus.UPLOADED,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "version-4-v1",
                null,
                now,
                now);

        when(jdbcTemplate.queryForObject(
                        contains("SELECT ingest_append_document_latest_version"),
                        eq(Boolean.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("doc-rollback"),
                        eq(3),
                        eq(4),
                        eq("ROLLBACK"),
                        eq(1),
                        eq("hash-v1"),
                        eq("v1.pdf"),
                        eq(128L),
                        eq("UPLOADED"),
                        isNull(),
                        eq(0),
                        eq(3),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(0),
                        isNull(),
                        eq("version-4-v1"),
                        isNull(),
                        isNull(),
                        any(),
                        any()))
                .thenReturn(Boolean.TRUE);
        when(jdbcTemplate.queryForObject(
                        contains("SELECT ingest_update_latest_document_version_processing"),
                        eq(Boolean.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("doc-rollback"),
                        eq("UPLOADED"),
                        eq("FAILED"),
                        eq("parse failed"),
                        eq(0),
                        isNull(),
                        eq("{}"),
                        eq("PARSE_FAILED"),
                        eq("parse failed"),
                        any(),
                        any()))
                .thenReturn(Boolean.TRUE);

        assertTrue(repository.appendRollbackVersion(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                documentId,
                3,
                rollbackVersion,
                now));
        assertTrue(repository.markFailed(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                documentId,
                UploadStatus.UPLOADED,
                "parse failed",
                "{}",
                "PARSE_FAILED",
                "parse failed",
                now,
                now));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForObject(sqlCaptor.capture(), eq(Boolean.class), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().get(0).contains("ingest_append_document_latest_version"));
        assertTrue(sqlCaptor.getAllValues().get(1).contains("ingest_update_latest_document_version_processing"));
        assertFalse(sqlCaptor.getAllValues().get(1).contains("ingest_append_document_latest_version"));
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
