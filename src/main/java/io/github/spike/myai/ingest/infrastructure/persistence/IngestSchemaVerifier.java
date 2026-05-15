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
 * ingest 表结构自检器。
 *
 * <p>该组件在应用启动时（ApplicationRunner）自动运行。它的核心目标是“强约束”数据库 Schema 的一致性：
 * <ul>
 *     <li>检查 document 主表、旧兼容镜像列与 document version 事实表是否存在。</li>
 *     <li>检查迁移期兼容索引与 version 事实查询索引是否符合预期。</li>
 * </ul>
 * 校验失败时会打印详细的修复指导 SQL 并直接抛出异常阻止应用启动，从而避免在运行时出现难以排查的数据一致性问题。
 */
@Component
public class IngestSchemaVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestSchemaVerifier.class);

    private static final String DOCUMENT_TABLE_NAME = "ingest_documents";
    private static final String VERSION_TABLE_NAME = "ingest_document_versions";
    private static final String LEGACY_UNIQUE_INDEX_NAME = "uk_ingest_documents_kb_file_hash";
    private static final String VERSION_FILE_HASH_INDEX_NAME = "idx_ingest_document_versions_file_hash";

    // 预期的索引定义 SQL，用于在日志中给出修复建议。
    private static final String EXPECTED_LEGACY_INDEX_SQL = """
            CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
            ON ingest_documents (kb_id, file_hash)
            WHERE file_hash IS NOT NULL AND status <> 'DELETED'
            """;

    private static final String EXPECTED_VERSION_FILE_HASH_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_ingest_document_versions_file_hash
            ON ingest_document_versions (file_hash)
            """;

    // 用于检查索引过滤条件是否包含 "status <> 'deleted'" 的正则表达式。
    private static final Pattern STATUS_NOT_DELETED_PATTERN =
            Pattern.compile("status.*<>.*'deleted'");

    // document 主表身份与 latest projection 列，缺失任何一列都将导致启动失败。
    private static final Set<String> REQUIRED_DOCUMENT_COLUMNS = Set.of(
            "document_id",
            "kb_id",
            "workspace_id",
            "latest_version_number",
            "latest_status",
            "latest_filename",
            "latest_version_origin_type",
            "retry_count",
            "retry_max",
            "split_version",
            "created_at",
            "updated_at");

    // 迁移期旧版本事实镜像列：只允许兼容写入，不允许作为新读路径事实源。
    private static final Set<String> LEGACY_COMPATIBILITY_COLUMNS = Set.of(
            "file_hash",
            "filename",
            "file_size",
            "status",
            "processing_metadata");

    // version 表承载真实版本级文件、处理、错误与来源事实。
    private static final Set<String> REQUIRED_VERSION_COLUMNS = Set.of(
            "document_id",
            "version_number",
            "version_origin_type",
            "rollback_from_version_number",
            "file_hash",
            "filename",
            "file_size",
            "status",
            "failure_reason",
            "retry_count",
            "retry_max",
            "next_retry_at",
            "last_error_code",
            "last_error_message",
            "last_error_at",
            "reprocess_count",
            "reprocess_requested_at",
            "split_version",
            "processing_metadata",
            "created_at",
            "updated_at");

    private final JdbcTemplate jdbcTemplate;
    private final IngestProperties ingestProperties;

    /**
     * 构造函数。
     *
     * <p>表结构初始化已切换为 Flyway，此处仅保留只读校验职责。
     */
    public IngestSchemaVerifier(JdbcTemplate jdbcTemplate, IngestProperties ingestProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.ingestProperties = ingestProperties;
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
        verifyRequiredColumns(DOCUMENT_TABLE_NAME, REQUIRED_DOCUMENT_COLUMNS, "document identity/latest projection");
        verifyRequiredColumns(DOCUMENT_TABLE_NAME, LEGACY_COMPATIBILITY_COLUMNS, "legacy compatibility mirror");
        verifyRequiredColumns(VERSION_TABLE_NAME, REQUIRED_VERSION_COLUMNS, "document version facts");
        verifyLegacyUniqueIndex();
        verifyVersionFileHashIndex();
        log.info("Ingest schema verification passed. tables={},{}", DOCUMENT_TABLE_NAME, VERSION_TABLE_NAME);
    }

    /**
     * 校验表中的列是否完整。
     * 通过查询 information_schema.columns 元数据表来实现。
     */
    private void verifyRequiredColumns(String tableName, Set<String> requiredColumns, String purpose) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                        """,
                tableName);
        Set<String> actual = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("column_name");
            if (value != null) {
                actual.add(String.valueOf(value).toLowerCase(Locale.ROOT));
            }
        }

        // 计算缺失的列。
        Set<String> missing = new LinkedHashSet<>(requiredColumns);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            fail(
                    "ingest schema check failed: missing required columns for " + purpose + " " + missing,
                    """
                            -- 参考修复：确认表结构与当前代码一致
                            -- 核心表：%s
                            -- 缺失列需通过 Flyway 迁移补齐
                            """.formatted(tableName));
        }
    }

    /**
     * 校验迁移期兼容唯一索引是否正确设置。
     * 特别是检查是否包含 "(kb_id, file_hash)" 以及对应的局部索引条件。
     */
    private void verifyLegacyUniqueIndex() {
        String indexDef = findIndexDefinition(DOCUMENT_TABLE_NAME, LEGACY_UNIQUE_INDEX_NAME);
        if (indexDef == null || indexDef.isBlank()) {
            fail(
                    "ingest schema check failed: required legacy compatibility index is missing: "
                            + LEGACY_UNIQUE_INDEX_NAME,
                    buildLegacyIndexRepairSuggestion());
            return;
        }

        String normalizedIndexDef = normalizeSql(indexDef);
        if (!normalizedIndexDef.contains("(kb_id,file_hash)")) {
            fail(
                    "ingest schema check failed: legacy unique index columns mismatch: " + indexDef,
                    buildLegacyIndexRepairSuggestion());
        }

        String predicate = extractLegacyIndexPredicate();
        if (!isExpectedLegacyPredicate(predicate)) {
            fail(
                    "ingest schema check failed: legacy unique index predicate mismatch: " + predicate,
                    buildLegacyIndexRepairSuggestion());
        }
    }

    /**
     * 校验 version 表文件哈希索引。
     */
    private void verifyVersionFileHashIndex() {
        String indexDef = findIndexDefinition(VERSION_TABLE_NAME, VERSION_FILE_HASH_INDEX_NAME);
        if (indexDef == null || indexDef.isBlank()) {
            fail(
                    "ingest schema check failed: required version file_hash index is missing: "
                            + VERSION_FILE_HASH_INDEX_NAME,
                    buildVersionFileHashIndexRepairSuggestion());
            return;
        }

        String normalizedIndexDef = normalizeSql(indexDef);
        if (!normalizedIndexDef.contains("onpublic." + VERSION_TABLE_NAME)
                || !normalizedIndexDef.contains("(file_hash)")) {
            fail(
                    "ingest schema check failed: version file_hash index mismatch: " + indexDef,
                    buildVersionFileHashIndexRepairSuggestion());
        }
    }

    private String findIndexDefinition(String tableName, String indexName) {
        return jdbcTemplate.query(
                """
                        SELECT indexdef
                        FROM pg_indexes
                        WHERE schemaname = 'public'
                          AND tablename = ?
                          AND indexname = ?
                        """,
                rs -> rs.next() ? rs.getString(1) : null,
                tableName,
                indexName);
    }

    /**
     * 从 pg_index 系统目录中提取旧兼容索引的谓词表达式。
     */
    private String extractLegacyIndexPredicate() {
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
                    DOCUMENT_TABLE_NAME,
                    LEGACY_UNIQUE_INDEX_NAME);
        } catch (DataAccessException ex) {
            fail("ingest schema check failed: unable to inspect legacy index predicate", buildLegacyIndexRepairSuggestion(), ex);
            return null;
        }
    }

    /**
     * 判断提取到的谓词内容是否符合预期。
     */
    private static boolean isExpectedLegacyPredicate(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return false;
        }
        String normalized = normalizeSql(predicate);
        return normalized.contains("file_hashisnotnull")
                && STATUS_NOT_DELETED_PATTERN.matcher(normalized).find();
    }

    /**
     * 在校验失败时生成旧兼容索引修复指导 SQL 字符串。
     */
    private static String buildLegacyIndexRepairSuggestion() {
        return """
                -- 参考修复 SQL（请按变更窗口人工执行）
                DROP INDEX IF EXISTS uk_ingest_documents_kb_file_hash;
                %s;
                """.formatted(EXPECTED_LEGACY_INDEX_SQL.strip());
    }

    /**
     * 在校验失败时生成 version 表文件哈希索引修复指导 SQL 字符串。
     */
    private static String buildVersionFileHashIndexRepairSuggestion() {
        return """
                -- 参考修复 SQL（请按变更窗口人工执行）
                DROP INDEX IF EXISTS idx_ingest_document_versions_file_hash;
                %s;
                """.formatted(EXPECTED_VERSION_FILE_HASH_INDEX_SQL.strip());
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
