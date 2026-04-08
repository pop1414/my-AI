package io.github.spike.myai.ingest.infrastructure.vector;

import io.github.spike.myai.ingest.domain.model.Document;
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
import org.springframework.ai.document.Document.Builder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 基于 PGVector 的文档向量写入实现。
 */
@Component
public class PgVectorDocumentVectorIndexer implements DocumentVectorIndexer {

    private static final String SPLIT_VERSION = "v1";

    private final VectorStore vectorStore;

    public PgVectorDocumentVectorIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void index(Document document, List<String> chunks) {
        List<String> chunkIds = new ArrayList<>(chunks.size());
        List<org.springframework.ai.document.Document> vectorDocuments = new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            // 确定性 chunkId：同一 document + chunkIndex + splitVersion 必须得到同一 ID。
            String chunkId = deterministicChunkId(document.documentId().value(), i, SPLIT_VERSION);
            chunkIds.add(chunkId);

            // 按设计文档填充最小元数据集合，支持追踪与过滤。
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", document.documentId().value());
            metadata.put("kbId", document.kbId());
            metadata.put("chunkIndex", i);
            metadata.put("sourceFile", document.filename());
            metadata.put("contentHash", sha256(chunkText));
            metadata.put("splitVersion", SPLIT_VERSION);

            Builder builder = org.springframework.ai.document.Document.builder();
            vectorDocuments.add(builder.id(chunkId).text(chunkText).metadata(metadata).build());
        }

        // 幂等写入：先删再写，避免重试造成重复膨胀。
        // 这里等价于 upsert 语义，便于在不同向量存储实现上保持一致行为。
        vectorStore.delete(chunkIds);
        vectorStore.add(vectorDocuments);
    }

    private static String deterministicChunkId(String documentId, int chunkIndex, String splitVersion) {
        // PGVector 默认按 UUID 处理文档主键，这里生成“确定性 UUID”保证幂等与兼容。
        String seed = documentId + "|" + chunkIndex + "|" + splitVersion;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 not available", ex);
        }
    }
}
