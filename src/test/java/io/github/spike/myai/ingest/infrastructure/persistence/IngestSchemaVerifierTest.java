package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class IngestSchemaVerifierTest {

    @Test
    @DisplayName("关键列与索引均满足预期时，自检通过")
    void run_shouldPass_whenSchemaValid() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();

        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredColumns());
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT indexdef")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn("CREATE UNIQUE INDEX uk_ingest_documents_kb_file_hash ON public.ingest_documents (kb_id, file_hash) WHERE file_hash IS NOT NULL AND status <> 'DELETED'");
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT pg_get_expr")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn("((file_hash IS NOT NULL) AND (status <> 'DELETED'::text))");

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertDoesNotThrow(() -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少关键列时，应拒绝启动")
    void run_shouldFail_whenMissingColumn() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredColumnsWithoutStatus());

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("缺少唯一索引时，应拒绝启动")
    void run_shouldFail_whenMissingIndex() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredColumns());
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT indexdef")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn(null);

        IngestSchemaVerifier verifier = new IngestSchemaVerifier(jdbcTemplate, properties);
        assertThrows(IllegalStateException.class, () -> verifier.run(null));
    }

    @Test
    @DisplayName("索引谓词不包含 status <> 'DELETED' 时，应拒绝启动")
    void run_shouldFail_whenIndexPredicateMismatch() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        IngestProperties properties = new IngestProperties();
        when(jdbcTemplate.queryForList(anyString(), eq("ingest_documents")))
                .thenReturn(requiredColumns());
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT indexdef")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn("CREATE UNIQUE INDEX uk_ingest_documents_kb_file_hash ON public.ingest_documents (kb_id, file_hash)");
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null && sql.contains("SELECT pg_get_expr")),
                        Mockito.<ResultSetExtractor<String>>any(),
                        eq("ingest_documents"),
                        eq("uk_ingest_documents_kb_file_hash")))
                .thenReturn("(file_hash IS NOT NULL)");

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

    private static List<Map<String, Object>> requiredColumns() {
        return List.of(
                row("document_id"),
                row("kb_id"),
                row("workspace_id"),
                row("file_hash"),
                row("filename"),
                row("status"),
                row("retry_count"),
                row("retry_max"),
                row("split_version"),
                row("created_at"),
                row("updated_at"));
    }

    private static List<Map<String, Object>> requiredColumnsWithoutStatus() {
        return List.of(
                row("document_id"),
                row("kb_id"),
                row("workspace_id"),
                row("file_hash"),
                row("filename"),
                row("retry_count"),
                row("retry_max"),
                row("split_version"),
                row("created_at"),
                row("updated_at"));
    }

    private static Map<String, Object> row(String columnName) {
        return Map.of("column_name", columnName);
    }
}
