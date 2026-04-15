package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

/**
 * 基于 PGVector 的问答检索适配器。
 *
 * <p>该类是问答检索能力的基础设施实现（Adapter），负责将领域端口
 * {@link io.github.spike.myai.qa.domain.port.ChunkRetrievalPort}
 * 适配到 Spring AI 的 {@link VectorStore}。
 *
 * <p>职责：
 * <ul>
 *   <li>接收应用层检索请求并构造向量检索参数；</li>
 *   <li>将向量库返回文档映射为领域分块模型；</li>
 *   <li>对元数据不完整的结果做防御性过滤并记录告警。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>适配器只负责“检索与映射”，不处理业务层 kb 过滤规则；</li>
 *   <li>对异常或脏数据采用“尽量跳过单条、不中断整体”的容错思路；</li>
 *   <li>返回结果顺序沿用底层向量检索返回顺序（通常为相似度降序）。</li>
 * </ul>
 */
@Component
public class PgVectorChunkRetrievalAdapter implements ChunkRetrievalPort {

    /** 当前适配器使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(PgVectorChunkRetrievalAdapter.class);
    /** 向量文档元数据中“文档 ID”字段名。 */
    private static final String METADATA_DOCUMENT_ID = "documentId";
    /** 向量文档元数据中“知识库 ID”字段名。 */
    private static final String METADATA_KB_ID = "kbId";
    /** 向量文档元数据中“分块序号”字段名。 */
    private static final String METADATA_CHUNK_INDEX = "chunkIndex";

    /** Spring AI 向量存储抽象，底层可由 PGVector 等实现。 */
    private final VectorStore vectorStore;

    /**
     * 构造检索适配器。
     *
     * @param vectorStore 向量存储组件，由 Spring 容器注入
     */
    public PgVectorChunkRetrievalAdapter(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 执行相似度检索并映射为领域分块结果。
     *
     * <p>处理流程：
     * <ol>
     *   <li>对空问题做快速返回，避免无意义向量检索；</li>
     *   <li>构造检索请求并保护 topK 最小值；</li>
     *   <li>调用向量库检索；</li>
     *   <li>将每条命中文档转换为 {@link RetrievedChunk}；</li>
     *   <li>过滤转换失败条目并返回有效结果。</li>
     * </ol>
     *
     * @param question 用户问题
     * @param topK 期望召回上限
     * @return 映射后的分块列表
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK) {
        if (question == null || question.isBlank()) {
            // 与上层约定：空问题不抛错，直接返回空结果。
            return List.of();
        }
        // topK 做下限保护，避免传入 0 或负值导致底层行为不确定。
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(Math.max(1, topK))
                .build();

        // 注意：这里不做 kbId 过滤，过滤策略由应用层统一处理，避免职责交叉。
        return vectorStore.similaritySearch(searchRequest).stream()
                .map(this::toRetrievedChunk)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将向量检索结果映射为领域分块对象。
     *
     * <p>仅当 {@code documentId} 与 {@code kbId} 同时有效时才返回结果，
     * 否则记录告警并丢弃该条，避免污染上层问答流程。
     */
    private RetrievedChunk toRetrievedChunk(org.springframework.ai.document.Document chunkDocument) {
        Map<String, Object> metadata = chunkDocument.getMetadata();
        String documentId = asString(metadata.get(METADATA_DOCUMENT_ID));
        String kbId = asString(metadata.get(METADATA_KB_ID));
        if (documentId == null || documentId.isBlank() || kbId == null || kbId.isBlank()) {
            // 文档主键或知识库标识缺失会影响上层过滤与溯源，直接丢弃该条。
            log.warn("Skip invalid vector metadata. metadata={}", metadata);
            return null;
        }
        int chunkIndex = asInt(metadata.get(METADATA_CHUNK_INDEX), -1);
        // 向量文档正文允许为空，统一归一为空字符串，减少上层空值判断分支。
        String content = chunkDocument.getText() == null ? "" : chunkDocument.getText();
        return new RetrievedChunk(documentId, kbId, chunkIndex, content);
    }

    /**
     * 将任意元数据对象安全转为字符串。
     *
     * <p>约定：
     * <ul>
     *   <li>输入为 null 时返回 null；</li>
     *   <li>非 null 时使用 {@link String#valueOf(Object)} 转换。</li>
     * </ul>
     */
    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * 将任意元数据对象安全转为整数。
     *
     * <p>支持 Number 与可解析数字字符串；
     * 其他类型或解析失败时返回默认值。
     *
     * @param value 待转换元数据值
     * @param defaultValue 转换失败时使用的默认值
     * @return 解析得到的整数或默认值
     */
    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
