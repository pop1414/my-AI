package io.github.spike.myai.knowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JdbcKnowledgeBaseQueryRepository 单元测试。
 */
class JdbcKnowledgeBaseQueryRepositoryTest {

    @Test
    @DisplayName("listIndexedKnowledgeBases 应按 INDEXED 口径聚合")
    void listIndexedKnowledgeBases_shouldUseIndexedAggregationSql() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcKnowledgeBaseQueryRepository repository = new JdbcKnowledgeBaseQueryRepository(jdbcTemplate);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        repository.listIndexedKnowledgeBases();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("WHERE status = 'INDEXED'"));
        assertTrue(sqlCaptor.getValue().contains("GROUP BY kb_id"));
    }
}
