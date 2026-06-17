package io.github.spike.myai.ingest.infrastructure.persistence;

import io.github.spike.myai.ingest.domain.model.ChunkContentType;
import io.github.spike.myai.ingest.domain.model.ChunkMetadata;
import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentChunkPreviewRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的文档分块预览查询仓储实现（基础设施层适配器）。
 *
 * <p>该类是 {@link DocumentChunkPreviewRepository} 端口接口的 JDBC 实现，
 * 位于六边形架构的<b>基础设施层</b>。
 *
 * <h3>查询策略</h3>
 * <ol>
 *   <li>通过 {@code JOIN ingest_documents} 关联文档表，
 *       利用文档表的 {@code workspace_id} 实现工作区隔离；</li>
 *   <li>从 {@code vector_store} 表的 JSONB {@code metadata} 字段中
 *       提取 {@code chunkIndex / sourceFile / contentHash / splitVersion / chunkMetadata}
 *       等分块元信息；</li>
 *   <li>使用 {@code COALESCE} 处理 JSONB 字段可能为 {@code NULL} 的边界情况；</li>
 *   <li>按 {@code chunkIndex} 升序排列，保证预览顺序与原文一致。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 * @see DocumentChunkPreviewRepository
 */
@Repository
public class JdbcDocumentChunkPreviewRepository implements DocumentChunkPreviewRepository {

    /**
     * 分块预览查询 SQL。
     *
     * <p>通过 {@code JOIN ingest_documents} 确保工作区隔离：
     * 仅当文档表与向量存储表的工作区匹配时才返回结果。
     *
     * <p>JSONB 字段通过 {@code COALESCE} 提供默认值：
     * <ul>
     *   <li>{@code chunkIndex} —— 默认 0，保证排序不报错；</li>
     *   <li>{@code content} —— 默认空字符串；</li>
     *   <li>{@code sourceFile / contentHash / splitVersion} —— 默认空字符串；</li>
     *   <li>{@code chunkMetadata} —— 默认空 JSON 对象。</li>
     * </ul>
     */
    private static final String FIND_BY_DOCUMENT_ID_SQL = """
            SELECT
              COALESCE((metadata->>'chunkIndex')::int, 0) AS chunk_index,
              COALESCE(content, '') AS content,
              COALESCE(LENGTH(content), 0) AS content_length,
              COALESCE(metadata->>'sourceFile', '') AS source_file,
              COALESCE(metadata->>'contentHash', '') AS content_hash,
              COALESCE(metadata->>'splitVersion', '') AS split_version,
              COALESCE(metadata->>'chunkMetadata', '{}') AS chunk_metadata
            FROM vector_store vs
            JOIN ingest_documents doc
              ON doc.document_id = vs.metadata->>'documentId'
            WHERE doc.workspace_id = ?
              AND metadata->>'documentId' = ?
              AND metadata->>'splitVersion' = ?
            -- chunkIndex 作为排序关键，保证预览顺序稳定
            ORDER BY (metadata->>'chunkIndex')::int ASC
            LIMIT ? OFFSET ?
            """;

    /**
     * 分块计数查询 SQL。
     *
     * <p>与 {@link #FIND_BY_DOCUMENT_ID_SQL} 共享相同的过滤条件，
     * 但不包含 LIMIT/OFFSET 和 ORDER BY。
     */
    private static final String COUNT_BY_DOCUMENT_ID_SQL = """
            SELECT COUNT(1)
            FROM vector_store vs
            JOIN ingest_documents doc
              ON doc.document_id = vs.metadata->>'documentId'
            WHERE doc.workspace_id = ?
              AND metadata->>'documentId' = ?
              AND metadata->>'splitVersion' = ?
            """;

    /**
     * {@link DocumentChunkPreview} 读模型的 JDBC 行映射器。
     *
     * <p>将向量存储表的分块元数据映射为领域读模型对象。
     * chunkMetadata 从 JSONB 字符串反序列化为 {@link ChunkMetadata}。
     */
    private static final RowMapper<DocumentChunkPreview> ROW_MAPPER = (rs, rowNum) -> new DocumentChunkPreview(
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getInt("content_length"),
            rs.getString("source_file"),
            rs.getString("content_hash"),
            rs.getString("split_version"),
            parseChunkMetadata(rs.getString("chunk_metadata")));

    /** Spring JDBC 模板，用于执行所有数据库操作 */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造器注入。
     *
     * @param jdbcTemplate Spring JDBC 模板
     */
    public JdbcDocumentChunkPreviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按文档 ID 查询分块预览数据（分页）。
     *
     * <p>以 {@code documentId + splitVersion} 过滤，避免混入历史版本分块。
     * 通过 JOIN 文档表保证工作区隔离。
     *
     * @param workspaceId  工作区标识
     * @param documentId   文档资产 ID
     * @param splitVersion 分块版本
     * @param limit        最大返回条数
     * @param offset       起始偏移
     * @return 分块列表（按 chunkIndex 升序）
     */
    @Override
    public List<DocumentChunkPreview> findByDocumentId(
            String workspaceId,
            DocumentId documentId,
            String splitVersion,
            int limit,
            int offset) {
        return jdbcTemplate.query(
                FIND_BY_DOCUMENT_ID_SQL,
                ROW_MAPPER,
                workspaceId,
                documentId.value(),
                splitVersion,
                limit,
                offset);
    }

    /**
     * 查询指定文档 + 分块版本的总分块数。
     *
     * <p>用于前端分页组件计算总页数。{@code null} 兜底为 0。
     *
     * @param workspaceId  工作区标识
     * @param documentId   文档资产 ID
     * @param splitVersion 分块版本
     * @return 分块总数（无数据时返回 0）
     */
    @Override
    public int countByDocumentId(String workspaceId, DocumentId documentId, String splitVersion) {
        // queryForObject 在无结果时可能返回 null，兜底为 0
        Integer count = jdbcTemplate.queryForObject(
                COUNT_BY_DOCUMENT_ID_SQL,
                Integer.class,
                workspaceId,
                documentId.value(),
                splitVersion);
        return count == null ? 0 : count;
    }

    /**
     * 从 JSONB 字符串解析 ChunkMetadata。
     *
     * <p>支持新格式 {@code {"headings":[...],"pageNumber":0,"contentType":"PARAGRAPH"}}。
     * 格式不合法或为空时返回默认 ChunkMetadata。
     */
    private static ChunkMetadata parseChunkMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return ChunkMetadata.of(null, 0, null);
        }
        // 简单 JSON 解析：提取 headings、pageNumber、contentType
        try {
            List<String> headings = extractJsonStringArray(json, "headings");
            int pageNumber = extractJsonInt(json, "pageNumber", 0);
            String contentTypeStr = extractJsonString(json, "contentType");
            ChunkContentType contentType = contentTypeStr != null
                    ? ChunkContentType.valueOf(contentTypeStr)
                    : ChunkContentType.PARAGRAPH;
            return ChunkMetadata.of(headings, pageNumber, contentType);
        } catch (Exception ex) {
            // 格式不兼容（如旧版 {"heading":"..."} 格式），返回默认值
            return ChunkMetadata.of(null, 0, null);
        }
    }

    /** 从 JSON 字符串中提取指定 key 的字符串数组值（简易解析）。 */
    private static List<String> extractJsonStringArray(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return List.of();
        }
        start += pattern.length();
        int arrayStart = json.indexOf('[', start);
        if (arrayStart < 0) {
            return List.of();
        }
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayEnd < 0) {
            return List.of();
        }
        String arrayContent = json.substring(arrayStart + 1, arrayEnd).trim();
        if (arrayContent.isEmpty()) {
            return List.of();
        }
        // 按逗号分割，去除引号
        List<String> result = new java.util.ArrayList<>();
        for (String item : arrayContent.split(",")) {
            String trimmed = item.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                result.add(trimmed.substring(1, trimmed.length() - 1)
                        .replace("\\\"", "\"").replace("\\\\", "\\"));
            }
        }
        return List.copyOf(result);
    }

    /** 从 JSON 字符串中提取指定 key 的 int 值（简易解析）。 */
    private static int extractJsonInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return defaultValue;
        }
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return defaultValue;
        }
        return Integer.parseInt(json.substring(start, end));
    }

    /** 从 JSON 字符串中提取指定 key 的字符串值（简易解析）。 */
    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return null;
        }
        start += pattern.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }
}
