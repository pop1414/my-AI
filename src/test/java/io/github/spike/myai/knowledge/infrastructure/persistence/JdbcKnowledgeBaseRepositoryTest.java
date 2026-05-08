package io.github.spike.myai.knowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @DisplayName("listKnowledgeBases 应基于主数据 left join INDEXED 文档统计")
    void listKnowledgeBases_shouldUseLeftJoinAggregation() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseRepository repository = new JdbcKnowledgeBaseRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        repository.listKnowledgeBases();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("LEFT JOIN ingest_documents"));
        assertTrue(sqlCaptor.getValue().contains("doc.status = 'INDEXED'"));
        assertTrue(sqlCaptor.getValue().contains("GROUP BY kb.kb_id, kb.name, kb.description, kb.status, kb.created_at"));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY kb.created_at ASC, kb.kb_id ASC"));
    }
}
