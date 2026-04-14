package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ingest_documents 表结构自检器。
 *
 * <p>该组件在应用启动时（ApplicationRunner）自动运行。它的核心目标是“强约束”数据库 Schema 的一致性：
 * <ul>
 *     <li>检查关键列（如 file_hash, status）是否存在，防止代码中使用的 SQL 字段在数据库中缺失。</li>
 *     <li>检查关键的唯一索引（Unique Index）及其局部索引条件（Predicate）是否符合预期。</li>
 * </ul>
 * 校验失败时会打印详细的修复指导 SQL 并直接抛出异常阻止应用启动，从而避免在运行时出现难以排查的数据一致性问题。
 */
@Component
public class IngestSchemaVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestSchemaVerifier.class);

    private static final String TABLE_NAME = "ingest_documents"; // 待校验的目标表名
    private static final String UNIQUE_INDEX_NAME = "uk_ingest_documents_kb_file_hash"; // 预期的唯一索引名称

    // 预期的索引定义 SQL，用于在日志中给出修复建议
    private static final String EXPECTED_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
            ON ingest_documents (kb_id, file_hash)
            WHERE file_hash IS NOT NULL AND status <> 'DELETED'
            """;

    // 用于检查索引过滤条件是否包含 "status <> 'deleted'" 的正则表达式
    private static final Pattern STATUS_NOT_DELETED_PATTERN =
            Pattern.compile("status.*<>.*'deleted'");

    // 该表必须包含的列集合，缺失任何一列都将导致启动失败
    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "document_id",
            "kb_id",
            "file_hash",
            "filename",
            "status",
            "retry_count",
            "retry_max",
            "split_version",
            "created_at",
            "updated_at");

    private final JdbcTemplate jdbcTemplate;
    private final IngestProperties ingestProperties;

    @SuppressWarnings("unused")
    private final JdbcDocumentRepository jdbcDocumentRepository;

    /**
     * 构造函数。
     * 这里注入了 JdbcDocumentRepository 是为了建立 Bean 的初始化顺序依赖，
     * 确保在自检运行前，Repository 可能触发的初始化逻辑已经完成。
     */
    public IngestSchemaVerifier(
            JdbcTemplate jdbcTemplate,
            IngestProperties ingestProperties,
            JdbcDocumentRepository jdbcDocumentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.ingestProperties = ingestProperties;
        this.jdbcDocumentRepository = jdbcDocumentRepository;
    }

    /**
     * Spring Boot 启动后的回调方法。
     */
    @Override
    public void run(ApplicationArguments args) {
        // 如果开关关闭，则跳过检查（通常用于单元测试或特殊维护场景）
        if (!ingestProperties.getSchemaCheck().isEnabled()) {
            log.info("Skip ingest schema verification because myai.ingest.schema-check.enabled=false");
            return;
        }
        verifyRequiredColumns(); // 执行列存在性校验
        verifyUniqueIndex();     // 执行唯一索引及分片条件校验
        log.info("Ingest schema verification passed. table={}", TABLE_NAME);
    }

    /**
     * 校验表中的列是否完整。
     * 通过查询 information_schema.columns 元数据表来实现。
     */
    private void verifyRequiredColumns() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                TABLE_NAME);
        Set<String> actual = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("column_name");
            if (value != null) {
                actual.add(String.valueOf(value).toLowerCase(Locale.ROOT));
            }
        }

        // 计算缺失的列
        Set<String> missing = new LinkedHashSet<>(REQUIRED_COLUMNS);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            fail(
                    "ingest schema check failed: missing required columns " + missing,
                    """
                            -- 参考修复：确认表结构与当前代码一致
                            -- 核心表：ingest_documents
                            -- 缺失列需按 JdbcDocumentRepository INIT_SQL/升级 SQL 补齐
                            """);
        }
    }

    /**
     * 校验唯一索引是否正确设置。
     * 特别是检查是否包含 "(kb_id, file_hash)" 以及对应的局部索引条件。
     */
    private void verifyUniqueIndex() {
        // 1. 检查索引是否存在
        String indexDef = jdbcTemplate.query(
                """
                        SELECT indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND tablename = ?
                          AND indexname = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                TABLE_NAME,
                UNIQUE_INDEX_NAME);
        if (indexDef == null || indexDef.isBlank()) {
            fail(
                    "ingest schema check failed: required index is missing: " + UNIQUE_INDEX_NAME,
                    buildIndexRepairSuggestion());
            return;
        }

        // 2. 规范化索引定义 SQL 并检查覆盖的列
        String normalizedIndexDef = normalizeSql(indexDef);
        if (!normalizedIndexDef.contains("(kb_id,file_hash)")) {
            fail(
                    "ingest schema check failed: unique index columns mismatch: " + indexDef,
                    buildIndexRepairSuggestion());
        }

        // 3. 检查索引谓词（WHERE 子句）。在 PostgreSQL 中，谓词存储在 indpred 字段中
        String predicate = extractIndexPredicate();
        if (!isExpectedPredicate(predicate)) {
            fail(
                    "ingest schema check failed: unique index predicate mismatch: " + predicate,
                    buildIndexRepairSuggestion());
        }
    }

    /**
     * 从 pg_index 系统目录中提取索引的谓词表达式。
     */
    private String extractIndexPredicate() {
        try {
            return jdbcTemplate.query(
                    """
                            SELECT pg_get_expr(i.indpred, i.indrelid) AS predicate
                            FROM pg_index i
                            JOIN pg_class idx ON idx.oid = i.indexrelid
                            JOIN pg_class tbl ON tbl.oid = i.indrelid
                            JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
                            WHERE ns.nspname = 'public'
                              AND tbl.relname = ?
                              AND idx.relname = ?
                            """,
                    rs -> rs.next() ? rs.getString("predicate") : null,
                    TABLE_NAME,
                    UNIQUE_INDEX_NAME);
        } catch (DataAccessException ex) {
            fail("ingest schema check failed: unable to inspect index predicate", buildIndexRepairSuggestion(), ex);
            return null;
        }
    }

    /**
     * 判断提取到的谓词内容是否符合预期：
     * 必须排除掉 status 为 'DELETED' 的记录，以支持逻辑删除后的重新上传。
     */
    private static boolean isExpectedPredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return false;
        }
        String normalized = normalizeSql(predicate);
        // 期望谓词包含 file_hash IS NOT NULL 以及对 status 的过滤
        return normalized.contains("file_hashisnotnull")
                && STATUS_NOT_DELETED_PATTERN.matcher(normalized).find();
    }

    /**
     * 在校验失败时生成修复指导 SQL 字符串。
     */
    private static String buildIndexRepairSuggestion() {
        return """
                -- 参考修复 SQL（请按变更窗口人工执行）
                DROP INDEX IF EXISTS uk_ingest_documents_kb_file_hash;
                CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
                ON ingest_documents (kb_id, file_hash)
                WHERE file_hash IS NOT NULL AND status <> 'DELETED';
                """;
    }

    /**
     * 对 SQL 字符串进行规范化处理，去除引号、空字符、类型转换等，便于字符串匹配比对。
     */
    private static String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replace("\"", "")
                .replace("::text", "")
                .replaceAll("\\s+", "");
    }

    /**
     * 终止程序启动并打印错误信息及修复提示。
     */
    private static void fail(String message, String suggestion) {
        log.error("{}\n{}", message, suggestion);
        throw new IllegalStateException(message + "\n" + suggestion);
    }

    private static void fail(String message, String suggestion, Exception ex) {
        log.error("{}\n{}", message, suggestion, ex);
        throw new IllegalStateException(message + "\n" + suggestion, ex);
    }
}
