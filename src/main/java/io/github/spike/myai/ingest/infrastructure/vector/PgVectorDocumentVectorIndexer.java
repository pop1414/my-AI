package io.github.spike.myai.ingest.infrastructure.vector;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document.Builder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 PGVector 的文档向量写入实现。
 *
 * <p>该类负责将解析后的文档切片（DocumentChunk）转换为向量模型支持的格式，
 * 并持久化到带有 pgvector 扩展的 PostgreSQL 数据库中。
 *
 * <p>设计核心：
 * <ul>
 *     <li>确定性 ID：通过文档标识、切片索引和版本号生成唯一的 UUID，实现写入幂等。</li>
 *     <li>元数据增强：在向量数据中存储业务元数据（如 kbId, docId, version），支持后续的高效检索过滤。</li>
 *     <li>版本化隔离：通过 splitVersion 确保重处理过程中新旧数据的平滑切换。</li>
 * </ul>
 */
@Component
public class PgVectorDocumentVectorIndexer implements DocumentVectorIndexer {

    private static final Logger log = LoggerFactory.getLogger(PgVectorDocumentVectorIndexer.class);

    /**
     * 按 documentId 和 splitVersion 清理向量的 SQL。
     * 由于 Spring AI 的 VectorStore 未直接暴露基于元数据的删除接口，
     * 故此处通过 JdbcTemplate 直接操作底层表（metadata 字段为 JSONB 类型）。
     */
    private static final String DELETE_BY_DOCUMENT_AND_VERSION_SQL = """
            DELETE FROM vector_store
            WHERE metadata->>'documentId' = ? AND metadata->>'splitVersion' = ?
            """;

    private final VectorStore vectorStore;
    /**
     * 用于执行原生的元数据 SQL 删除动作。
     */
    private final JdbcTemplate jdbcTemplate;

    public PgVectorDocumentVectorIndexer(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 将解析后的文档块批量索引到向量库中。
     *
     * @param document 关联的文档聚合根，包含元数据及当前分块版本号
     * @param chunks   待写入的文档文本切片列表
     */
    @Override
    @Transactional
    public void index(Document document, List<DocumentChunk> chunks) {
        List<String> chunkIds = new ArrayList<>(chunks.size());
        List<org.springframework.ai.document.Document> vectorDocuments = new ArrayList<>(chunks.size());
        // splitVersion 关键：用于标识当前处理批次的版本，支持重洗数据时的版本切换。
        String splitVersion = document.splitVersion();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String chunkText = chunk.content();

            // 确定性 chunkId：使用 (docId + index + version) 作为输入生成 UUID。
            // 这确保了在重试场景下，即使生成了相同的切片，在库中也只会覆盖而非新增。
            String chunkId = deterministicChunkId(document.documentId().value(), i, splitVersion);
            chunkIds.add(chunkId);

            // 填充元数据映射：这些字段会在检索时作为 Filter 被使用。
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", document.documentId().value()); // 溯源文档
            metadata.put("kbId", document.kbId());                    // 知识库隔离
            metadata.put("chunkIndex", i);                          // 切片顺序
            metadata.put("sourceFile", document.filename());        // 原始文件名
            metadata.put("contentHash", sha256(chunkText));         // 文本指纹（去重/校验）
            metadata.put("splitVersion", splitVersion);             // 逻辑版本锁
            if (chunk.sourceHint() != null && !chunk.sourceHint().isBlank()) {
                // 存储来自解析器的额外提示（如 PDF 页码、章节标题等 JSON 信息）。
                metadata.put("sourceHint", chunk.sourceHint());
            }

            // 构建 Spring AI 标准的 Document 对象（包含文本、元数据和 ID）。
            Builder builder = org.springframework.ai.document.Document.builder();
            vectorDocuments.add(builder.id(chunkId).text(chunkText).metadata(metadata).build());
        }

        // 幂等策略执行：
        // 1. 先根据确定的 chunkIds 删除可能存在的旧向量。
        // 2. 将此批次的新向量批量加入库中。
        try {
            log.info("Updating vector index for document: {}, version: {}",
                    document.documentId().value(), document.splitVersion());

            vectorStore.delete(chunkIds);
            vectorStore.add(vectorDocuments);

            log.debug("Vector store updated successfully for document: {}", document.documentId().value());
        }catch (Exception e){
            log.error("Failed to update vector store for document: {}. Error: {}",
                    document.documentId().value(), e.getMessage());
            throw e; // 抛出异常以触发表回滚
        }

    }

    /**
     * 执行特定版本的向量数据逻辑清理。
     * 通常由 ReprocessDocumentApplicationService 调用。
     *
     * @param documentId   待清理的文档标识
     * @param splitVersion 需被物理移除的特定版本（旧版本）
     */
    @Override
    public void deleteByDocumentIdAndSplitVersion(DocumentId documentId, String splitVersion) {
        // 仅删除指定版本的向量，避免新版本写入被误删。
        jdbcTemplate.update(DELETE_BY_DOCUMENT_AND_VERSION_SQL, documentId.value(), splitVersion);
    }

    /**
     * 生成基于内容的确定性 UUID。
     * 保证在同一个文档、同一个索引位置、以及同一个版本号下生成的 ID 永久一致。
     */
    private static String deterministicChunkId(String documentId, int chunkIndex, String splitVersion) {
        // PGVector 默认按 UUID 处理文档主键，这里生成“确定性 UUID”保证幂等与兼容。
        String seed = documentId + "|" + chunkIndex + "|" + splitVersion;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 计算字符串内容的 SHA-256 摘要，返回十六进制格式。
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 not available", ex);
        }
    }
}
