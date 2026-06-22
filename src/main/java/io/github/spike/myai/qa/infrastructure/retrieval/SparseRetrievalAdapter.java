package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 PostgreSQL tsvector 的 BM25 近似全文检索适配器。
 *
 * <p>该类是问答检索能力的基础设施实现（Adapter），负责将领域端口
 * {@link io.github.spike.myai.qa.domain.port.ChunkRetrievalPort}
 * 适配到 PostgreSQL 原生全文检索（tsvector + ts_rank）。</p>
 *
 * <p>职责：
 * <ul>
 *   <li>接收应用层检索请求并构造 BM25 检索 SQL；</li>
 *   <li>复用 {@link ScopeFilterBuilder} 处理 scope 过滤；</li>
 *   <li>将查询结果映射为领域分块模型，score 填充 ts_rank 值。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>使用 {@link JdbcTemplate} 直接执行 SQL，不经过 Spring AI VectorStore；</li>
 *   <li>SQL SELECT 中直接提取 metadata JSON 字段，减少 Java 端解析逻辑；</li>
 *   <li>与 {@link PgVectorChunkRetrievalAdapter} 实现同一端口，作为 Sparse 检索路径的平行实现。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@Component
public class SparseRetrievalAdapter implements ChunkRetrievalPort {

    /** 当前适配器使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(SparseRetrievalAdapter.class);

    /** 初始文档版本号（用于 legacy splitVersion 兼容解析）。 */
    private static final int INITIAL_DOCUMENT_VERSION_NUMBER = 1;

    /** 基础检索 SQL — 不含 scope 过滤，使用 zhparser 中文分词配置。 */
    private static final String BASE_SQL = """
            SELECT id, content,
                   metadata->>'documentId' AS document_id,
                   metadata->>'kbId' AS kb_id,
                   (metadata->>'chunkIndex')::int AS chunk_index,
                   (metadata->>'documentVersionNumber')::int AS version_number,
                   metadata->>'splitVersion' AS split_version,
                   metadata->>'sourceFile' AS source_file,
                   metadata->>'sourceUpdatedAt' AS source_updated_at,
                   ts_rank(content_tsv, query) AS rank
            FROM vector_store, plainto_tsquery('chinese', ?) query
            WHERE content_tsv @@ query""";

    /** 排序与分页子句。 */
    private static final String ORDER_AND_LIMIT = " ORDER BY rank DESC LIMIT ?";

    /**
     * 中文口语停用词正则 —— 在送入 plainto_tsquery 前清除，避免 AND 语义下的"一票否决"。
     *
     * <p>设计原则：
     * <ul>
     *   <li>按长度降序排列，保证"什么"先于"什""么"匹配；</li>
     *   <li>仅清除口语功能词（代词、系动词、助动词、语气词）；</li>
     *   <li>保留技术动词（"配置""优化""部署"）和副词（"最""近似"），它们有检索价值。</li>
     * </ul>
     *
     * @see <a href="docs/adr/ADR-0008-zhparser-pos-mapping.md">ADR-0008：词性映射策略演进</a>
     */
    private static final Pattern STOPWORDS_PATTERN = Pattern.compile(
            "到底|什么|怎么|怎样|如何|为啥|为何|哪个|哪些|"
            + "是否|可是|但是|而是|不是|也是|就是|还是|"
            + "该|有|要|是|的|得|这|那|哪|我|你|他|她|它|们|吗|呢|吧|啊|哦|嗯");

    /** JdbcTemplate 用于直接 SQL 查询。 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造检索适配器。
     *
     * @param jdbcTemplate JDBC 模板，由 Spring 容器注入
     */
    public SparseRetrievalAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行 BM25 全文检索。
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数
     * @return 命中的分块列表，按 ts_rank 降序
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK) {
        return similaritySearch(question, topK, null);
    }

    /**
     * 执行带可问答版本范围的 BM25 全文检索。
     *
     * @param question 用户问题文本
     * @param topK 最大召回条数
     * @param scope 可问答文档版本范围；为空时不附加版本过滤
     * @return 命中的分块列表，按 ts_rank 降序
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        SqlScopeCondition scopeCondition = ScopeFilterBuilder.toSqlCondition(scope);

        String processed = preprocessQuery(question);
        log.debug("Sparse query: raw=[{}], processed=[{}]", question, processed);

        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> params = new ArrayList<>();
        params.add(processed);

        if (!scopeCondition.whereClause().isEmpty()) {
            sql.append(" AND ").append(scopeCondition.whereClause());
            params.addAll(scopeCondition.params());
        }

        sql.append(ORDER_AND_LIMIT);
        params.add(Math.max(1, topK));

        return jdbcTemplate.query(sql.toString(), SparseRetrievalAdapter::mapRow, params.toArray())
                .stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 将 ResultSet 行映射为领域分块对象。
     *
     * <p>仅当 {@code documentId} 与 {@code kbId} 同时有效时才返回结果，
     * 否则记录告警并丢弃该行，与 Dense 路径行为一致。</p>
     */
    static RetrievedChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String documentId = rs.getString("document_id");
        String kbId = rs.getString("kb_id");
        if (documentId == null || documentId.isBlank() || kbId == null || kbId.isBlank()) {
            log.warn("Skip invalid vector metadata. rowNum={}, documentId={}, kbId={}", rowNum, documentId, kbId);
            return null;
        }

        int chunkIndex = rs.getInt("chunk_index");
        if (rs.wasNull()) {
            log.warn("chunkIndex is NULL in vector metadata, defaulting to 0. rowNum={}", rowNum);
        }
        String content = rs.getString("content");
        if (content == null) {
            content = "";
        }

        Integer sourceVersionNumber = resolveSourceVersionNumber(rs);
        String sourceFilename = rs.getString("source_file");
        Instant sourceUpdatedAt = parseInstant(rs.getString("source_updated_at"));

        double rawScore = rs.getDouble("rank");
        double score = Double.isFinite(rawScore) ? rawScore : 0.0;

        return new RetrievedChunk(
                documentId,
                kbId,
                chunkIndex,
                content,
                sourceVersionNumber,
                sourceFilename,
                sourceUpdatedAt,
                score);
    }

    /**
     * 解析分块对应的文档版本号。
     *
     * <p>优先读取显式的 {@code version_number}。为空时尝试从 {@code split_version}
     * 兼容解析（例如 {@code version-2-v1} → 2）。</p>
     */
    private static Integer resolveSourceVersionNumber(java.sql.ResultSet rs) throws java.sql.SQLException {
        int versionNumber = rs.getInt("version_number");
        if (!rs.wasNull()) {
            return versionNumber;
        }
        return parseVersionNumberFromSplitVersion(rs.getString("split_version"));
    }

    /**
     * 从版本化分块标识中解析文档版本号。
     *
     * @param splitVersion 分块版本，例如 {@code version-2-v1}
     * @return 文档版本号；非版本链格式时返回 null
     */
    private static Integer parseVersionNumberFromSplitVersion(String splitVersion) {
        if (splitVersion == null || !splitVersion.startsWith("version-")) {
            return null;
        }
        String remainder = splitVersion.substring("version-".length());
        int separatorIndex = remainder.indexOf("-v");
        if (separatorIndex <= 0) {
            return null;
        }
        try {
            return Integer.parseInt(remainder.substring(0, separatorIndex));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 将 ISO-8601 字符串安全转为 Instant。
     */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 查询预处理 —— 清除中文口语停用词，保留技术术语。
     *
     * <p>配合 V13 的完整词性映射（v + d 均保留），在查询端做减法：
     * 数据库索引覆盖全部有价值的词性，但查询时过滤掉系动词、代词、语气词等
     * 会在 plainto_tsquery 的 AND 语义下导致"一票否决"的噪声词。</p>
     *
     * <p>典型示例：
     * <pre>
     * "我该怎么配置PGVector数据库环境" → "配置PGVector数据库环境"
     * "PGVector到底是什么技术"         → "PGVector 技术"
     * "向量检索的核心原理是什么"       → "向量检索 核心原理"
     * </pre>
     *
     * @param query 用户原始查询文本
     * @return 清除停用词后的查询文本，多余空白已压缩
     */
    static String preprocessQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        return STOPWORDS_PATTERN.matcher(query).replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
