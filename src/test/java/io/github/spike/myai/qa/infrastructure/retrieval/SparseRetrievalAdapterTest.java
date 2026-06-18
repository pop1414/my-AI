package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * SparseRetrievalAdapter 单元测试。
 *
 * <p>测试分为两类：
 * <ul>
 *   <li><b>RowMapper 直接测试</b>：调用 {@code SparseRetrievalAdapter.mapRow(rs, rowNum)}
 *   直接验证 ResultSet → RetrievedChunk 的映射逻辑，不涉及 JdbcTemplate；</li>
 *   <li><b>编排测试</b>：通过 mock {@link JdbcTemplate} 验证 SQL 构造和参数拼装
 *   —— 此类测试验证的是 Java 层的参数组装逻辑而非 SQL 正确性，
 *   完整的 SQL 端到端验证应由集成测试（真实 PostgreSQL）覆盖。</li>
 * </ul>
 */
class SparseRetrievalAdapterTest {

    private static final Instant NOW = Instant.parse("2026-06-18T10:00:00Z");

    // === 空查询防御（无 mock）===

    @Test
    @DisplayName("similaritySearch 应在查询为 null 时返回空列表")
    void similaritySearch_shouldReturnEmptyList_whenQuestionIsNull() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        List<RetrievedChunk> result = adapter.similaritySearch(null, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("similaritySearch 应在查询为空白时返回空列表")
    void similaritySearch_shouldReturnEmptyList_whenQuestionIsBlank() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        List<RetrievedChunk> result = adapter.similaritySearch("   ", 5);

        assertTrue(result.isEmpty());
    }

    // === RowMapper 直接测试（不 mock JdbcTemplate）===

    @Test
    @DisplayName("mapRow 应正确映射 ResultSet 全部字段到 RetrievedChunk")
    void mapRow_shouldMapAllFieldsCorrectly() throws SQLException {
        ResultSet rs = mockResultSet(
                "doc-1", "default", 3, "Flyway migration guide",
                2, false, "doc-1-v2.pdf", "2026-05-09T10:00:00Z", 0.85);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertEquals("doc-1", chunk.documentId());
        assertEquals("default", chunk.kbId());
        assertEquals(3, chunk.chunkIndex());
        assertEquals("Flyway migration guide", chunk.content());
        assertEquals(2, chunk.sourceVersionNumber());
        assertEquals("doc-1-v2.pdf", chunk.sourceFilename());
        assertEquals(Instant.parse("2026-05-09T10:00:00Z"), chunk.sourceUpdatedAt());
        assertEquals(0.85, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("mapRow 应将 ts_rank 正确映射到 score")
    void mapRow_shouldMapScoreCorrectly() throws SQLException {
        ResultSet rs = mockResultSet(
                "doc-score", "default", 0, "scored content",
                1, false, "f.pdf", "2026-01-01T00:00:00Z", 0.42);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertEquals(0.42, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("mapRow 应在 score 为 NaN 时安全回退为 0.0")
    void mapRow_shouldFallbackScoreToZero_whenScoreIsNaN() throws SQLException {
        ResultSet rs = mockResultSet(
                "doc-nan", "default", 0, "nan score",
                1, false, "f.pdf", "2026-01-01T00:00:00Z", Double.NaN);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertEquals(0.0, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("mapRow 应在 score 为 Infinity 时安全回退为 0.0")
    void mapRow_shouldFallbackScoreToZero_whenScoreIsInfinity() throws SQLException {
        ResultSet rs = mockResultSet(
                "doc-inf", "default", 0, "inf score",
                1, false, "f.pdf", "2026-01-01T00:00:00Z", Double.POSITIVE_INFINITY);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertEquals(0.0, chunk.score(), 0.0001);
    }

    @Test
    @DisplayName("mapRow 应在 documentId 为 null 时返回 null")
    void mapRow_shouldReturnNull_whenDocumentIdIsNull() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("document_id")).thenReturn(null);
        when(rs.getString("kb_id")).thenReturn("default");

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertNull(chunk);
    }

    @Test
    @DisplayName("mapRow 应在 kbId 为 null 时返回 null")
    void mapRow_shouldReturnNull_whenKbIdIsNull() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("document_id")).thenReturn("doc-1");
        when(rs.getString("kb_id")).thenReturn(null);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertNull(chunk);
    }

    @Test
    @DisplayName("mapRow 应从 splitVersion 兼容解析来源版本号")
    void mapRow_shouldResolveVersionFromSplitVersion() throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("document_id")).thenReturn("doc-legacy");
        when(rs.getString("kb_id")).thenReturn("default");
        when(rs.getInt("chunk_index")).thenReturn(1);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getString("content")).thenReturn("legacy content");
        when(rs.getInt("version_number")).thenReturn(0);
        when(rs.wasNull()).thenReturn(false, true); // chunk_index exists, version_number IS NULL
        when(rs.getString("split_version")).thenReturn("version-2-v1");
        when(rs.getString("source_file")).thenReturn("doc-legacy-v2.pdf");
        when(rs.getString("source_updated_at")).thenReturn("2026-01-01T00:00:00Z");
        when(rs.getDouble("rank")).thenReturn(0.5);

        RetrievedChunk chunk = SparseRetrievalAdapter.mapRow(rs, 0);

        assertEquals("doc-legacy", chunk.documentId());
        assertEquals(2, chunk.sourceVersionNumber());
        assertEquals("doc-legacy-v2.pdf", chunk.sourceFilename());
    }

    // === 编排测试（mock JdbcTemplate 验证参数组装与 SQL 构造）===
    // 注意：以下测试 mock JdbcTemplate 仅为验证 Java 层参数组装逻辑，
    // 完整的 SQL 端到端正确性需通过集成测试（真实 PostgreSQL）验证。

    @Test
    @DisplayName("similaritySearch 应对 topK 做下限保护（最低为 1）")
    @SuppressWarnings("unchecked")
    void similaritySearch_shouldProtectTopKLowerBound() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        adapter.similaritySearch("test", 0);

        Mockito.verify(jdbcTemplate).query(
                Mockito.contains("LIMIT ?"),
                any(RowMapper.class),
                Mockito.eq(new Object[]{"test", 1}));
    }

    @Test
    @DisplayName("similaritySearch 应将 scope 过滤正确拼接到 WHERE 子句")
    @SuppressWarnings("unchecked")
    void similaritySearch_shouldAppendScopeFilterToWhereClause() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        var scope = List.of(
                new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW),
                new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", NOW));

        adapter.similaritySearch("test question", 5, scope);

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        assertTrue(sql.contains("metadata->>'documentId'"), "SQL 应包含 scope 过滤条件");
        assertTrue(sql.contains("AND"), "SQL 应包含 AND 拼接 scope");

        assertEquals(8, args.length, "参数数量：question + 6 scope params + topK");
        assertEquals("test question", args[0]);
        assertEquals("doc-1", args[1]);
        assertEquals(2, args[2]);
        assertEquals("version-2-v1", args[3]);
        assertEquals("doc-2", args[4]);
        assertEquals(4, args[5]);
        assertEquals("version-4-v1", args[6]);
        assertEquals(5, args[7]);
    }

    @Test
    @DisplayName("similaritySearch 应在 scope 为空时不拼接额外条件")
    @SuppressWarnings("unchecked")
    void similaritySearch_shouldNotAppendScopeFilter_whenScopeIsEmpty() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        adapter.similaritySearch("test question", 5, List.of());

        org.mockito.ArgumentCaptor<String> sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> argsCaptor = org.mockito.ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        assertTrue(!sql.contains("AND metadata->>'documentId'"),
                "scope 为空时 SQL 不应包含 scope 过滤条件（AND metadata...）");
        assertEquals(2, args.length, "参数数量：question + topK");
        assertEquals("test question", args[0]);
        assertEquals(5, args[1]);
    }

    @Test
    @DisplayName("similaritySearch 应正确处理含 Flyway 关键词的检索")
    @SuppressWarnings("unchecked")
    void similaritySearch_shouldRetrieveChunksMatchingFlywayKeyword() throws SQLException {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

        ResultSet rs1 = mockResultSet(
                "doc-flyway", "kb-1", 0, "Flyway 是一个数据库迁移工具",
                1, false, "flyway-guide.pdf", "2026-06-01T00:00:00Z", 0.65);
        ResultSet rs2 = mockResultSet(
                "doc-spring", "kb-1", 1, "Spring Boot 集成 Flyway 配置",
                1, false, "spring-boot.pdf", "2026-06-01T00:00:00Z", 0.55);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RowMapper<RetrievedChunk> rowMapper = invocation.getArgument(1);
                    RetrievedChunk chunk1 = rowMapper.mapRow(rs1, 0);
                    RetrievedChunk chunk2 = rowMapper.mapRow(rs2, 1);
                    return List.of(chunk1, chunk2);
                });

        List<RetrievedChunk> result = adapter.similaritySearch("Flyway 配置", 5);

        assertEquals(2, result.size());
        assertEquals("doc-flyway", result.get(0).documentId());
        assertEquals(0.65, result.get(0).score(), 0.0001);
        assertEquals("doc-spring", result.get(1).documentId());
        assertEquals(0.55, result.get(1).score(), 0.0001);
        assertTrue(result.get(0).score() > result.get(1).score(),
                "含更多 Flyway 关键词命中的文档应有更高的 ts_rank 分数");
    }

    // === 测试辅助方法 ===

    /**
     * 构造一个 mock ResultSet，预置所有 mapRow 需要的列值。
     */
    private static ResultSet mockResultSet(
            String documentId, String kbId, int chunkIndex, String content,
            int versionNumber, boolean versionIsNull,
            String sourceFile, String sourceUpdatedAt, double rank) throws SQLException {
        ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.getString("document_id")).thenReturn(documentId);
        when(rs.getString("kb_id")).thenReturn(kbId);
        when(rs.getInt("chunk_index")).thenReturn(chunkIndex);
        when(rs.getString("content")).thenReturn(content);
        when(rs.getInt("version_number")).thenReturn(versionNumber);
        when(rs.wasNull()).thenReturn(false, versionIsNull); // chunk_index not null, version_number depends on param
        when(rs.getString("split_version")).thenReturn(null);
        when(rs.getString("source_file")).thenReturn(sourceFile);
        when(rs.getString("source_updated_at")).thenReturn(sourceUpdatedAt);
        when(rs.getDouble("rank")).thenReturn(rank);
        return rs;
    }
}
