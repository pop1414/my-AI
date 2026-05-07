package io.github.spike.myai.knowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.infrastructure.persistence.JdbcDocumentRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcKnowledgeBaseRepositoryTest {

    @Test
    @DisplayName("构造初始化应创建主表、唯一索引并执行 default/backfill 迁移")
    void constructor_shouldInitializeSchemaAndBackfill() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository jdbcDocumentRepository = Mockito.mock(JdbcDocumentRepository.class);

        new JdbcKnowledgeBaseRepository(jdbcTemplate, jdbcDocumentRepository);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(4)).execute(sqlCaptor.capture());
        String combined = String.join("\n", sqlCaptor.getAllValues());
        assertTrue(combined.contains("CREATE TABLE IF NOT EXISTS knowledge_bases"));
        assertTrue(combined.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_knowledge_bases_kb_id"));
        assertTrue(combined.contains("WHERE NOT EXISTS"));
        assertTrue(combined.contains("SELECT DISTINCT kb_id"));
    }

    @Test
    @DisplayName("listKnowledgeBases 应基于主数据 left join INDEXED 文档统计")
    void listKnowledgeBases_shouldUseLeftJoinAggregation() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        JdbcDocumentRepository jdbcDocumentRepository = Mockito.mock(JdbcDocumentRepository.class);
        JdbcKnowledgeBaseRepository repository = new JdbcKnowledgeBaseRepository(jdbcTemplate, jdbcDocumentRepository);
        when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        repository.listKnowledgeBases();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("LEFT JOIN ingest_documents"));
        assertTrue(sqlCaptor.getValue().contains("doc.status = 'INDEXED'"));
    }
}
