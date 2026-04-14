package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
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
    @DisplayName("构造初始化应替换唯一索引并保留 DELETED 过滤策略")
    void constructor_shouldRecreateUniqueIndexWithDeletedFilter() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcDocumentRepository(jdbcTemplate);

        verify(jdbcTemplate, atLeastOnce()).execute(contains("DROP INDEX IF EXISTS uk_ingest_documents_kb_file_hash"));
        verify(jdbcTemplate, atLeastOnce())
                .execute(contains("WHERE file_hash IS NOT NULL AND status <> 'DELETED'"));
    }

    @Test
    @DisplayName("findByKbIdAndFileHash 查询应包含 DELETED 过滤条件")
    void findByKbIdAndFileHash_shouldExcludeDeletedStatus() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("kb-1"), eq("hash-1")))
                .thenReturn(List.of());

        repository.findByKbIdAndFileHash("kb-1", "hash-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq("kb-1"), eq("hash-1"));
        assertTrue(sqlCaptor.getValue().contains("status <> 'DELETED'"));
    }

    @Test
    @DisplayName("markDeleting 与 markDeleted/rollbackDeleting 应返回更新结果")
    void deleteStateMethods_shouldReflectUpdatedRows() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository repository = new JdbcDocumentRepository(jdbcTemplate);
        DocumentId documentId = new DocumentId("doc-1");
        Instant now = Instant.now();

        when(jdbcTemplate.update(contains("SET status = 'DELETING'"), any(), eq("doc-1"), eq("INDEXED")))
                .thenReturn(1);
        when(jdbcTemplate.update(contains("SET status = 'DELETED'"), any(), eq("doc-1")))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("WHERE document_id = ? AND status = 'DELETING'"), eq("FAILED"), any(), eq("doc-1")))
                .thenReturn(1);

        assertTrue(repository.markDeleting(documentId, UploadStatus.INDEXED, now));
        assertFalse(repository.markDeleted(documentId, now));
        assertTrue(repository.rollbackDeleting(documentId, UploadStatus.FAILED, now));
    }
}
