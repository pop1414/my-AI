package io.github.spike.myai.qa.infrastructure.retrieval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorFilterExpressionConverter;

/**
 * ScopeFilterBuilder 单元测试。
 */
class ScopeFilterBuilderTest {

    private static final Instant NOW = Instant.parse("2026-06-17T10:00:00Z");

    @Nested
    @DisplayName("toFilterExpression 测试")
    class ToFilterExpressionTests {

        @Test
        @DisplayName("空 scope 应返回 null")
        void toFilterExpression_shouldReturnNull_whenScopeIsNull() {
            assertNull(ScopeFilterBuilder.toFilterExpression(null));
        }

        @Test
        @DisplayName("空列表 scope 应返回 null")
        void toFilterExpression_shouldReturnNull_whenScopeIsEmpty() {
            assertNull(ScopeFilterBuilder.toFilterExpression(List.of()));
        }

        @Test
        @DisplayName("单个文档 scope 应生成包含 documentId 和版本条件的 Filter.Expression")
        void toFilterExpression_shouldBuildCorrectExpression_forSingleDocument() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);

            assertNotNull(expression);
            String expr = expression.toString();
            assertTrue(expr.contains("documentId"), "应包含 documentId");
            assertTrue(expr.contains("doc-1"), "应包含文档 ID 值");
            assertTrue(expr.contains("documentVersionNumber"), "应包含 documentVersionNumber");
            assertTrue(expr.contains("splitVersion"), "应包含 splitVersion");
            assertTrue(expr.contains("version-2-v1"), "应包含版本化 splitVersion");
        }

        @Test
        @DisplayName("多个文档 scope 应生成 OR 组合表达式")
        void toFilterExpression_shouldBuildOrCombination_forMultipleDocuments() {
            var scope = List.of(
                    new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW),
                    new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);

            assertNotNull(expression);
            String expr = expression.toString();
            assertTrue(expr.contains("doc-1"), "应包含 doc-1");
            assertTrue(expr.contains("doc-2"), "应包含 doc-2");
        }

        @Test
        @DisplayName("version=1 的文档应包含 legacy splitVersion='v1' 条件")
        void toFilterExpression_shouldIncludeLegacySplitVersion_forVersion1() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 1, 1, "doc-1-v1.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);

            assertNotNull(expression);
            String expr = expression.toString();
            assertTrue(expr.contains("splitVersion"), "应包含 splitVersion 键");
            assertTrue(expr.contains("version-1-v1"), "应包含 version-1-v1");
            // "Value[value=v1]" 不是 "Value[value=version-1-v1]" 的子串，可独立验证 legacy 条件
            assertTrue(expr.contains("Value[value=v1]"), "应包含 legacy splitVersion='v1' 独立条件");
        }

        @Test
        @DisplayName("version>1 的文档不应包含 legacy splitVersion='v1' 条件")
        void toFilterExpression_shouldNotIncludeLegacySplitVersion_forVersionGreaterThan1() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);

            assertNotNull(expression);
            String expr = expression.toString();
            // version>1 时应有 version-2-v1，但不应有独立的 "v1" 条件
            assertTrue(expr.contains("version-2-v1"), "应包含 version-2-v1");
        }

        @Test
        @DisplayName("混合版本 scope（v=1 + v>1）应生成正确的 Filter.Expression")
        void toFilterExpression_shouldHandleMixedVersions() {
            var scope = List.of(
                    new AskableDocumentVersion("doc-1", 1, 1, "doc-1-v1.pdf", NOW),
                    new AskableDocumentVersion("doc-2", 3, 3, "doc-2-v3.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);

            assertNotNull(expression);
            String expr = expression.toString();
            assertTrue(expr.contains("doc-1"), "应包含 doc-1");
            assertTrue(expr.contains("doc-2"), "应包含 doc-2");
            // doc-1(v=1) 应有 legacy "v1"，doc-2(v=3) 无
            assertTrue(expr.contains("Value[value=v1]"), "version=1 的文档应含 legacy v1");
            assertTrue(expr.contains("version-3-v1"), "version=3 的文档应含 version-3-v1");
        }

        @Test
        @DisplayName("生成的 Filter.Expression 应兼容 PGVector 转换器")
        void toFilterExpression_shouldBePgVectorCompatible() {
            var scope = List.of(
                    new AskableDocumentVersion("doc-1", 1, 1, "doc-1-v1.pdf", NOW),
                    new AskableDocumentVersion("doc-2", 3, 3, "doc-2-v3.pdf", NOW));

            Filter.Expression expression = ScopeFilterBuilder.toFilterExpression(scope);
            PgVectorFilterExpressionConverter converter = new PgVectorFilterExpressionConverter();

            assertNotNull(expression);
            assertDoesNotThrow(() -> converter.convertExpression(expression),
                    "PGVector 转换器应能处理生成的 Filter.Expression");
        }
    }

    @Nested
    @DisplayName("toSqlCondition 测试")
    class ToSqlConditionTests {

        @Test
        @DisplayName("空 scope 应返回空 whereClause 和空 params")
        void toSqlCondition_shouldReturnEmpty_whenScopeIsNull() {
            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(null);

            assertEquals("", condition.whereClause());
            assertTrue(condition.params().isEmpty());
        }

        @Test
        @DisplayName("空列表 scope 应返回空 whereClause 和空 params")
        void toSqlCondition_shouldReturnEmpty_whenScopeIsEmpty() {
            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(List.of());

            assertEquals("", condition.whereClause());
            assertTrue(condition.params().isEmpty());
        }

        @Test
        @DisplayName("单个文档 scope 应生成正确的 SQL 片段和参数列表")
        void toSqlCondition_shouldBuildCorrectSql_forSingleDocument() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW));

            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);

            String where = condition.whereClause();
            List<Object> params = condition.params();

            // SQL 应包含 metadata->>'documentId' 条件
            assertTrue(where.contains("metadata->>'documentId' = ?"), "应包含 documentId 条件");
            assertTrue(where.contains("metadata->>'documentVersionNumber'"), "应包含 documentVersionNumber 条件");
            assertTrue(where.contains("metadata->>'splitVersion'"), "应包含 splitVersion 条件");

            // 参数顺序：documentId, versionNumber, splitVersion
            assertEquals(3, params.size(), "应有 3 个参数");
            assertEquals("doc-1", params.get(0));
            assertEquals(2, params.get(1));
            assertEquals("version-2-v1", params.get(2));
        }

        @Test
        @DisplayName("多个文档 scope 应生成 OR 组合的 SQL")
        void toSqlCondition_shouldBuildOrCombination_forMultipleDocuments() {
            var scope = List.of(
                    new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW),
                    new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", NOW));

            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);

            String where = condition.whereClause();
            List<Object> params = condition.params();

            assertTrue(where.contains(" OR "), "多文档间应有 OR 连接");
            assertTrue(where.contains("metadata->>'documentId'"), "应包含 documentId 条件");

            // 参数顺序：doc1.documentId, doc1.versionNumber, doc1.splitVersion,
            //           doc2.documentId, doc2.versionNumber, doc2.splitVersion
            assertEquals(6, params.size(), "应有 6 个参数");
            assertEquals("doc-1", params.get(0));
            assertEquals(2, params.get(1));
            assertEquals("version-2-v1", params.get(2));
            assertEquals("doc-2", params.get(3));
            assertEquals(4, params.get(4));
            assertEquals("version-4-v1", params.get(5));
        }

        @Test
        @DisplayName("version=1 的文档应包含 legacy splitVersion 条件")
        void toSqlCondition_shouldIncludeLegacySplitVersion_forVersion1() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 1, 1, "doc-1-v1.pdf", NOW));

            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);

            String where = condition.whereClause();
            List<Object> params = condition.params();

            // version=1 应有 4 个参数：documentId, versionNumber, version-1-v1, v1
            assertEquals(4, params.size(), "version=1 应有 4 个参数（含 legacy v1）");
            assertEquals("doc-1", params.get(0));
            assertEquals(1, params.get(1));
            assertEquals("version-1-v1", params.get(2));
            assertEquals("v1", params.get(3));
        }

        @Test
        @DisplayName("SqlScopeCondition.params() 应返回不可变列表")
        void sqlScopeCondition_shouldReturnUnmodifiableParams() {
            var scope = List.of(new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", NOW));
            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);

            List<Object> params = condition.params();
            assertThrows(UnsupportedOperationException.class, () -> params.add("test"),
                    "params 列表应为不可变");
        }

        @Test
        @DisplayName("params 顺序应与 ? 占位符一致")
        void toSqlCondition_shouldAlignParamsWithPlaceholders() {
            var scope = List.of(
                    new AskableDocumentVersion("aaa", 1, 1, "a.pdf", NOW),
                    new AskableDocumentVersion("bbb", 5, 3, "b.pdf", NOW));

            SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);

            String where = condition.whereClause();
            List<Object> params = condition.params();

            // 统计 ? 占位符数量
            long placeholderCount = where.chars().filter(c -> c == '?').count();
            assertEquals(params.size(), placeholderCount,
                    "参数数量应与 ? 占位符数量一致");
        }
    }
}
