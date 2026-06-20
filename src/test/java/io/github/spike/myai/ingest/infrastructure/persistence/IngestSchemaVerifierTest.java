package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

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
class IngestSchemaVerifierTest {

    @Test
    @DisplayName("关键列与索引均满足预期时，自检通过")
    void run_shouldPass_whenSchemaValid() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        stubValidSchema(jdbcTemplate);

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertDoesNotThrow(() -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少主表兼容镜像列时，应拒绝启动")
    void run_shouldFail_whenMissingDocumentCompatibilityColumn() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredDocumentColumnsWithoutStatus());

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少 version 事实列时，应拒绝启动")
    void run_shouldFail_whenMissingVersionFactColumn() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredDocumentColumns());
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_document_versions")))
                .thenReturn(requiredVersionColumnsWithoutFileHash());

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少旧兼容唯一索引时，应拒绝启动")
    void run_shouldFail_whenMissingLegacyIndex() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredDocumentColumns());
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_document_versions")))
                .thenReturn(requiredVersionColumns());
        whenIndexDefinition(jdbcTemplate, "ingest_documents", "uk_ingest_documents_kb_file_hash", null);

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("旧兼容索引谓词不包含 status <> 'DELETED' 时，应拒绝启动")
    void run_shouldFail_whenLegacyIndexPredicateMismatch() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredDocumentColumns());
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_document_versions")))
                .thenReturn(requiredVersionColumns());
        whenIndexDefinition(
                jdbcTemplate,
                "ingest_documents",
                "uk_ingest_documents_kb_file_hash",
                "CREATE UNIQUE INDEX uk_ingest_documents_kb_file_hash ON public.ingest_documents (kb_id, file_hash)");
        whenLegacyPredicate(jdbcTemplate, "(file_hash IS NOT NULL)");

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少 version 文件哈希索引时，应拒绝启动")
    void run_shouldFail_whenVersionFactIndexMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        stubValidSchema(jdbcTemplate);
        whenIndexDefinition(jdbcTemplate, "ingest_document_versions", "idx_ingest_document_versions_file_hash", null);

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少 latest projection maintenance function 时，应拒绝启动")
    void run_shouldFail_whenLatestProjectionFunctionMissing() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        stubValidSchema(jdbcTemplate);
        whenFunctionExists(jdbcTemplate, "ingest_update_latest_document_version_processing", null);

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("配置关闭 schema-check 时，应跳过自检")
    void run_shouldSkip_whenSchemaCheckDisabled() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        properties.getSchemaCheck().setEnabled(false);
        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertDoesNotThrow(() -> verifier.run(null));
    }

    private static void stubValidSchema(JdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredDocumentColumns());
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_document_versions")))
                .thenReturn(requiredVersionColumns());
        whenIndexDefinition(
                jdbcTemplate,
                "ingest_documents",
                "uk_ingest_documents_kb_file_hash",
                "CREATE UNIQUE INDEX uk_ingest_documents_kb_file_hash ON public.ingest_documents (kb_id, file_hash) WHERE file_hash IS NOT NULL AND status <> 'DELETED'");
        whenLegacyPredicate(jdbcTemplate, "((file_hash IS NOT NULL) AND (status <> 'DELETED'::text))");
        whenIndexDefinition(
                jdbcTemplate,
                "ingest_document_versions",
                "idx_ingest_document_versions_file_hash",
                "CREATE INDEX idx_ingest_document_versions_file_hash ON public.ingest_document_versions USING btree (file_hash)");
        whenFunctionExists(jdbcTemplate, "ingest_append_document_latest_version", "ingest_append_document_latest_version");
        whenFunctionExists(
                jdbcTemplate,
                "ingest_update_latest_document_version_processing",
                "ingest_update_latest_document_version_processing");
    }

    private static void whenIndexDefinition(
            JdbcTemplate jdbcTemplate,
            String tableName,
            String indexName,
            String indexDefinition) {
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT indexdef")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq(tableName),
                        eq(indexName)))
                .thenReturn(indexDefinition);
    }

    private static void whenLegacyPredicate(JdbcTemplate jdbcTemplate, String predicate) {
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT pg_get_expr")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn(predicate);
    }

    private static void whenFunctionExists(
            JdbcTemplate jdbcTemplate,
            String functionName,
            String resolvedFunctionName) {
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT routine_name")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq(functionName)))
                .thenReturn(resolvedFunctionName);
    }

    private static List<Map<String, Object>> requiredDocumentColumns() {
        return List.of(
                row("document_id"),
                row("kb_id"),
                row("workspace_id"),
                row("latest_version_number"),
                row("latest_status"),
                row("latest_filename"),
                row("latest_version_origin_type"),
                row("retry_count"),
                row("retry_max"),
                row("split_version"),
                row("file_hash"),
                row("filename"),
                row("file_size"),
                row("status"),
                row("processing_metadata"),
                row("created_at"),
                row("updated_at"));
    }

    private static List<Map<String, Object>> requiredDocumentColumnsWithoutStatus() {
        return List.of(
                row("document_id"),
                row("kb_id"),
                row("workspace_id"),
                row("latest_version_number"),
                row("latest_status"),
                row("latest_filename"),
                row("latest_version_origin_type"),
                row("retry_count"),
                row("retry_max"),
                row("split_version"),
                row("file_hash"),
                row("filename"),
                row("file_size"),
                row("processing_metadata"),
                row("created_at"),
                row("updated_at"));
    }

    private static List<Map<String, Object>> requiredVersionColumns() {
        return List.of(
                row("document_id"),
                row("version_number"),
                row("version_origin_type"),
                row("rollback_from_version_number"),
                row("file_hash"),
                row("filename"),
                row("file_size"),
                row("status"),
                row("failure_reason"),
                row("retry_count"),
                row("retry_max"),
                row("next_retry_at"),
                row("last_error_code"),
                row("last_error_message"),
                row("last_error_at"),
                row("reprocess_count"),
                row("reprocess_requested_at"),
                row("split_version"),
                row("processing_metadata"),
                row("created_at"),
                row("updated_at"));
    }

    private static List<Map<String, Object>> requiredVersionColumnsWithoutFileHash() {
        return List.of(
                row("document_id"),
                row("version_number"),
                row("version_origin_type"),
                row("rollback_from_version_number"),
                row("filename"),
                row("file_size"),
                row("status"),
                row("failure_reason"),
                row("retry_count"),
                row("retry_max"),
                row("next_retry_at"),
                row("last_error_code"),
                row("last_error_message"),
                row("last_error_at"),
                row("reprocess_count"),
                row("reprocess_requested_at"),
                row("split_version"),
                row("processing_metadata"),
                row("created_at"),
                row("updated_at"));
    }

    private static Map<String, Object> row(String columnName) {
        return Map.of("column_name", columnName);
    }
}
