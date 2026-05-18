package io.github.spike.myai.knowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcKnowledgeBaseRepositoryTest {

    @Test
    @DisplayName("构造初始化不应再执行隐式 DDL")
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);

        new JdbcKnowledgeBaseRepository(jdbcTemplate);

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    @DisplayName("listKnowledgeBases 应基于 latest projection 统计 INDEXED 文档")
    void listKnowledgeBases_shouldUseLeftJoinAggregation() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseRepository repository = new JdbcKnowledgeBaseRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        repository.listKnowledgeBases(WorkspaceConstants.DEFAULT_WORKSPACE_ID);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID));
        assertTrue(sqlCaptor.getValue().contains("kb.workspace_id = ?"));
        assertTrue(sqlCaptor.getValue().contains("kb.status <> 'DELETED'"));
        assertTrue(sqlCaptor.getValue().contains("LEFT JOIN ingest_documents"));
        assertTrue(sqlCaptor.getValue().contains("doc.workspace_id = kb.workspace_id"));
        assertTrue(sqlCaptor.getValue().contains("doc.latest_status = 'INDEXED'"));
        assertTrue(sqlCaptor.getValue().contains("GROUP BY kb.kb_id, kb.workspace_id, kb.name, kb.description, kb.status, kb.created_at"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY kb.created_at ASC, kb.kb_id ASC"));
    }
}
