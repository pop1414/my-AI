package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.model.DocumentListFilter;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcDocumentListRepositoryTest {

    @Test
    @DisplayName("默认查询应排除 DELETED 并按创建时间倒序")
    void findPage_shouldExcludeDeletedByDefault() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentListRepository repository = new JdbcDocumentListRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq(20), eq(0)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID)))
                .thenReturn(0L);

        DocumentListPage page = repository.findPage(new DocumentListFilter(null, null, null, true, 20, 0));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq(20), eq(0));
        assertTrue(sqlCaptor.getValue().contains("d.workspace_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("d.latest_status <> 'DELETED'"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY created_at DESC"));
        assertTrue(sqlCaptor.getValue().contains("LIMIT ? OFFSET ?"));
        assertTrue(page.items().isEmpty());
    }

    @Test
    @DisplayName("显式查询 DELETED 时应使用 status 等值过滤")
    void findPage_shouldUseExplicitStatus_whenDeletedRequested() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentListRepository repository = new JdbcDocumentListRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                        any(String.class),
                        any(RowMapper.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("DELETED"),
                        eq(10),
                        eq(5)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                        any(String.class),
                        eq(Long.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("DELETED")))
                .thenReturn(0L);

        repository.findPage(new DocumentListFilter(null, UploadStatus.DELETED, null, false, 10, 5));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                eq("DELETED"),
                eq(10),
                eq(5));
        assertTrue(sqlCaptor.getValue().contains("d.latest_status = ?"));
        assertFalse(sqlCaptor.getValue().contains("d.latest_status <> 'DELETED'"));
    }

    @Test
    @DisplayName("组合筛选应包含 kbId 与 filename 模糊匹配条件")
    void findPage_shouldApplyKbIdAndFilenameFilter() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentListRepository repository = new JdbcDocumentListRepository(jdbcTemplate);
        when(jdbcTemplate.query(
                        any(String.class),
                        any(RowMapper.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("kb-1"),
                        eq("%合同%"),
                        eq(5),
                        eq(10)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                        any(String.class),
                        eq(Long.class),
                        eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                        eq("kb-1"),
                        eq("%合同%")))
                .thenReturn(2L);

        repository.findPage(new DocumentListFilter("kb-1", null, "合同", true, 5, 10));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                eq("kb-1"),
                eq("%合同%"),
                eq(5),
                eq(10));
        assertTrue(sqlCaptor.getValue().contains("d.kb_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("COALESCE(d.latest_filename, '') LIKE ?"));
        verify(jdbcTemplate).queryForObject(
                contains("COUNT(1)"),
                eq(Long.class),
                eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID),
                eq("kb-1"),
                eq("%合同%"));
    }
}
