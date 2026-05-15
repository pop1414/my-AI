package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.port.AskableDocumentVersionPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 基于版本事实表的问答可用版本查询适配器。
 *
 * <p>查询规则固定为：每个 document 独立选择版本号最大的 {@code INDEXED} 版本作为
 * 当前可问答版本，同时返回主表 latest projection 用于判断引用是否陈旧。</p>
 */
@Repository
public class JdbcAskableDocumentVersionAdapter implements AskableDocumentVersionPort {

    private static final String FIND_ASKABLE_VERSIONS_SQL = """
            SELECT d.document_id,
                   d.latest_version_number,
                   av.version_number AS askable_version_number,
                   av.filename AS source_filename,
                   av.updated_at AS source_updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions av
              ON av.document_id = d.document_id
             AND av.version_number = (
                   SELECT MAX(v.version_number)
                   FROM ingest_document_versions v
                   WHERE v.document_id = d.document_id
                     AND v.status = 'INDEXED'
             )
            WHERE d.workspace_id = :workspaceId
              AND d.document_id IN (:documentIds)
              AND d.latest_status <> 'DELETED'
              AND av.status = 'INDEXED'
            """;
    private static final String FIND_ASKABLE_VERSIONS_FOR_WORKSPACE_ADMIN_SQL = """
            SELECT d.document_id,
                   d.latest_version_number,
                   av.version_number AS askable_version_number,
                   av.filename AS source_filename,
                   av.updated_at AS source_updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions av
              ON av.document_id = d.document_id
             AND av.version_number = (
                   SELECT MAX(v.version_number)
                   FROM ingest_document_versions v
                   WHERE v.document_id = d.document_id
                     AND v.status = 'INDEXED'
             )
            WHERE d.workspace_id = :workspaceId
              AND d.kb_id = :kbId
              AND d.latest_status <> 'DELETED'
              AND av.status = 'INDEXED'
            ORDER BY d.document_id ASC
            """;
    private static final String FIND_ASKABLE_VERSIONS_FOR_MEMBER_SQL = """
            SELECT d.document_id,
                   d.latest_version_number,
                   av.version_number AS askable_version_number,
                   av.filename AS source_filename,
                   av.updated_at AS source_updated_at
            FROM ingest_documents d
            JOIN ingest_document_versions av
              ON av.document_id = d.document_id
             AND av.version_number = (
                   SELECT MAX(v.version_number)
                   FROM ingest_document_versions v
                   WHERE v.document_id = d.document_id
                     AND v.status = 'INDEXED'
             )
            LEFT JOIN document_grants dg
              ON dg.workspace_id = d.workspace_id
             AND dg.document_id = d.document_id
             AND dg.user_id = :userId
             AND dg.status = 'ACTIVE'
            WHERE d.workspace_id = :workspaceId
              AND d.kb_id = :kbId
              AND d.latest_status <> 'DELETED'
              AND av.status = 'INDEXED'
              AND COALESCE(dg.permission, '') <> 'DOC_DENY'
              AND (
                  dg.permission IN ('DOC_ALLOW_READ', 'DOC_ALLOW_MANAGE')
                  OR EXISTS (
                      SELECT 1
                      FROM knowledge_base_grants kg
                      WHERE kg.workspace_id = d.workspace_id
                        AND kg.kb_id = d.kb_id
                        AND kg.user_id = :userId
                        AND kg.status = 'ACTIVE'
                        AND kg.role IN ('KB_MANAGER', 'KB_CONTRIBUTOR', 'KB_READER', 'KB_ASKER')
                  )
              )
            ORDER BY d.document_id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 构造问答可用版本查询适配器。
     *
     * @param jdbcTemplate 支持命名参数的 JDBC 模板
     */
    public JdbcAskableDocumentVersionAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量查询每个文档最近一个已索引版本。
     *
     * @param workspaceId 工作区标识
     * @param documentIds 待查询文档 ID 集合
     * @return 以 documentId 为键的可问答版本映射
     */
    @Override
    public Map<String, AskableDocumentVersion> findAskableVersions(String workspaceId, Collection<String> documentIds) {
        Set<String> normalizedDocumentIds = documentIds == null
                ? Set.of()
                : documentIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        if (workspaceId == null || workspaceId.isBlank() || normalizedDocumentIds.isEmpty()) {
            return Map.of();
        }

        var parameters = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("documentIds", normalizedDocumentIds);

        return jdbcTemplate.query(
                        FIND_ASKABLE_VERSIONS_SQL,
                        parameters,
                        (rs, rowNum) -> new AskableDocumentVersion(
                                rs.getString("document_id"),
                                rs.getInt("latest_version_number"),
                                rs.getInt("askable_version_number"),
                                rs.getString("source_filename"),
                                toInstant(rs.getTimestamp("source_updated_at"))))
                .stream()
                .collect(Collectors.toUnmodifiableMap(AskableDocumentVersion::documentId, Function.identity()));
    }

    /**
     * 查询用户可问答范围内的可召回文档版本。
     *
     * @param currentUser 当前用户上下文
     * @param kbId 知识库 ID
     * @return 可召回的文档版本集合
     */
    @Override
    public List<AskableDocumentVersion> findAskableVersionsForQuestion(CurrentUser currentUser, String kbId) {
        if (currentUser == null || kbId == null || kbId.isBlank()) {
            return List.of();
        }

        var parameters = new MapSqlParameterSource()
                .addValue("workspaceId", currentUser.workspaceId())
                .addValue("kbId", kbId)
                .addValue("userId", currentUser.userId());

        String sql = hasWorkspaceWideAccess(currentUser.workspaceRole())
                ? FIND_ASKABLE_VERSIONS_FOR_WORKSPACE_ADMIN_SQL
                : FIND_ASKABLE_VERSIONS_FOR_MEMBER_SQL;
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> toAskableDocumentVersion(
                rs.getString("document_id"),
                rs.getInt("latest_version_number"),
                rs.getInt("askable_version_number"),
                rs.getString("source_filename"),
                toInstant(rs.getTimestamp("source_updated_at"))));
    }

    private static AskableDocumentVersion toAskableDocumentVersion(
            String documentId,
            int latestVersionNumber,
            int askableVersionNumber,
            String sourceFilename,
            Instant sourceUpdatedAt) {
        return new AskableDocumentVersion(
                documentId,
                latestVersionNumber,
                askableVersionNumber,
                sourceFilename,
                sourceUpdatedAt);
    }

    private static boolean hasWorkspaceWideAccess(WorkspaceRole role) {
        return role == WorkspaceRole.WORKSPACE_OWNER || role == WorkspaceRole.WORKSPACE_ADMIN;
    }

    /**
     * 将 JDBC 时间戳安全转换为 Instant。
     *
     * @param timestamp JDBC 时间戳，可为空
     * @return 转换后的时间；入参为空时返回 null
     */
    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }
}
