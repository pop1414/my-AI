package io.github.spike.myai.qa.infrastructure.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.spike.myai.qa.domain.model.QueryType;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RetrievalEvalDatasetLoader 单元测试。
 *
 * @author spike
 * @since 1.0.0
 */
class RetrievalEvalDatasetLoaderTest {

    private RetrievalEvalDatasetLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RetrievalEvalDatasetLoader();
    }

    // === 正常加载 ===

    @Test
    @DisplayName("加载有效的评测数据集 — 20 条 QA pairs 全部正确解析")
    void load_shouldReturnAllSamples_whenValidDataset() {
        List<EvalSample> samples = loader.load("eval/retrieval-qa-pairs.json");

        assertThat(samples).hasSize(20);
    }

    @Test
    @DisplayName("加载有效的评测数据集 — PROCEDURAL 样本字段正确映射")
    void load_shouldMapFieldsCorrectly_whenProceduralSample() {
        List<EvalSample> samples = loader.load("eval/retrieval-qa-pairs.json");

        EvalSample first = samples.get(0);
        assertThat(first.question()).isEqualTo("Spring Boot 如何配置 Flyway 数据库迁移");
        assertThat(first.queryType()).isEqualTo(QueryType.PROCEDURAL);
        assertThat(first.relevantDocIds()).containsExactly("doc-flyway-config", "doc-spring-boot-setup");
        assertThat(first.relevanceLevels()).containsEntry("doc-flyway-config", RelevanceLevel.STRONG);
        assertThat(first.relevanceLevels()).containsEntry("doc-spring-boot-setup", RelevanceLevel.WEAK);
    }

    @Test
    @DisplayName("加载有效的评测数据集 — CHITCHAT 样本相关文档为空")
    void load_shouldHandleEmptyRelevantDocs_whenChitchatSample() {
        List<EvalSample> samples = loader.load("eval/retrieval-qa-pairs.json");

        EvalSample chitchatSample = samples.stream()
                .filter(s -> s.queryType() == QueryType.CHITCHAT)
                .findFirst()
                .orElseThrow();

        assertThat(chitchatSample.relevantDocIds()).isEmpty();
        assertThat(chitchatSample.relevanceLevels()).isEmpty();
    }

    @Test
    @DisplayName("加载有效的评测数据集 — 每种 QueryType 至少 3 条")
    void load_shouldCoverAllQueryTypes_whenValidDataset() {
        List<EvalSample> samples = loader.load("eval/retrieval-qa-pairs.json");

        for (QueryType type : QueryType.values()) {
            long count = samples.stream()
                    .filter(s -> s.queryType() == type)
                    .count();
            assertThat(count)
                    .as("QueryType %s 应至少有 3 条样本", type)
                    .isGreaterThanOrEqualTo(3);
        }
    }

    // === 格式校验异常（使用 loadFromStream 测试错误路径） ===

    @Test
    @DisplayName("格式校验 — 缺失 question 字段时抛出 IllegalArgumentException")
    void loadFromStream_shouldThrow_whenMissingQuestion() {
        String json = """
                [{"query_type": "FACTOID", "relevant_doc_ids": [], "relevance_levels": {}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失必填字段 'question'");
    }

    @Test
    @DisplayName("格式校验 — 缺失 query_type 字段时抛出 IllegalArgumentException")
    void loadFromStream_shouldThrow_whenMissingQueryType() {
        String json = """
                [{"question": "test", "relevant_doc_ids": [], "relevance_levels": {}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失必填字段 'query_type'");
    }

    @Test
    @DisplayName("格式校验 — 缺失 relevant_doc_ids 字段时抛出 IllegalArgumentException")
    void loadFromStream_shouldThrow_whenMissingRelevantDocIds() {
        String json = """
                [{"question": "test", "query_type": "FACTOID", "relevance_levels": {}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失必填字段 'relevant_doc_ids'");
    }

    @Test
    @DisplayName("格式校验 — query_type 非法值时抛出 IllegalArgumentException")
    void loadFromStream_shouldThrow_whenInvalidQueryType() {
        String json = """
                [{"question": "test", "query_type": "INVALID", "relevant_doc_ids": [], "relevance_levels": {}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query_type 非法值");
    }

    @Test
    @DisplayName("格式校验 — relevant_doc_ids 中 ID 缺少 relevance_levels 标注时抛出异常")
    void loadFromStream_shouldThrow_whenDocIdMissingRelevanceLevel() {
        String json = """
                [{"question": "test", "query_type": "FACTOID",
                  "relevant_doc_ids": ["doc-001"], "relevance_levels": {}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("在 relevance_levels 中缺少标注");
    }

    @Test
    @DisplayName("格式校验 — relevance_levels 中非法级别值时抛出异常")
    void loadFromStream_shouldThrow_whenInvalidRelevanceLevel() {
        String json = """
                [{"question": "test", "query_type": "FACTOID",
                  "relevant_doc_ids": ["doc-001"], "relevance_levels": {"doc-001": "invalid"}}]
                """;

        assertThatThrownBy(() -> loader.loadFromStream(toStream(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("的值非法");
    }

    @Test
    @DisplayName("格式校验 — 文件不存在时抛出 IllegalArgumentException")
    void load_shouldThrow_whenFileNotFound() {
        assertThatThrownBy(() -> loader.load("eval/nonexistent.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("评测数据集文件未找到");
    }

    // === 辅助方法 ===

    private static ByteArrayInputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
