package io.github.spike.myai.knowledge.infrastructure.persistence;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseDocumentCount;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseQueryRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的知识库只读查询仓储实现。
 *
 * <p>该类位于基础设施层（infrastructure），负责将领域端口
 * {@link io.github.spike.myai.knowledge.domain.port.KnowledgeBaseQueryRepository}
 * 映射为具体 SQL 查询。
 *
 * <p>当前版本不引入独立的 {@code knowledge_bases} 主数据表，
 * 因此直接基于 {@code ingest_documents} 表聚合得到知识库统计信息。
 */
@Repository
public class JdbcKnowledgeBaseQueryRepository implements KnowledgeBaseQueryRepository {

    /**
     * 查询每个知识库已索引文档数量的聚合 SQL。
     *
     * <p>按 {@code kb_id} 分组，仅统计状态为 {@code INDEXED} 的文档，
     * 最终按知识库标识升序返回，保证接口输出顺序稳定。
     */
    private static final String LIST_INDEXED_KNOWLEDGE_BASES_SQL = """
            SELECT kb_id, COUNT(*) AS indexed_document_count
            FROM ingest_documents
            WHERE status = 'INDEXED'
            GROUP BY kb_id
            ORDER BY kb_id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KnowledgeBaseDocumentCount> listIndexedKnowledgeBases() {
        // 将 ResultSet 映射为领域模型，供应用层继续组装返回结果。
        return jdbcTemplate.query(
                LIST_INDEXED_KNOWLEDGE_BASES_SQL,
                (rs, rowNum) -> new KnowledgeBaseDocumentCount(
                        rs.getString("kb_id"),
                        rs.getLong("indexed_document_count")));
    }
}
