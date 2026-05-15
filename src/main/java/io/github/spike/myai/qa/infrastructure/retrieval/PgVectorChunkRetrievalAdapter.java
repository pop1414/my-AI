package io.github.spike.myai.qa.infrastructure.retrieval;

import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
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
    /** 向量文档元数据中“文档版本号”字段名。 */
    private static final String METADATA_DOCUMENT_VERSION_NUMBER = "documentVersionNumber";
    /** 向量文档元数据中“来源文件名”字段名。 */
    private static final String METADATA_SOURCE_FILE = "sourceFile";
    /** 向量文档元数据中“来源版本更新时间”字段名。 */
    private static final String METADATA_SOURCE_UPDATED_AT = "sourceUpdatedAt";
    /** 向量文档元数据中“分块版本”字段名。 */
    private static final String METADATA_SPLIT_VERSION = "splitVersion";
    /** 初始文档版本号。 */
    private static final int INITIAL_DOCUMENT_VERSION_NUMBER = 1;
    /** 历史初始向量使用的分块版本号。 */
    private static final String LEGACY_INITIAL_SPLIT_VERSION = "v1";

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
        return similaritySearch(question, topK, null);
    }

    /**
     * 执行带可问答版本范围的相似度检索。
     *
     * @param question 用户问题
     * @param topK 期望召回上限
     * @param scope 可问答文档版本范围；为空时表示不附加版本过滤
     * @return 映射后的分块列表
     */
    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope) {
        if (question == null || question.isBlank()) {
            // 与上层约定：空问题不抛错，直接返回空结果。
            return List.of();
        }
        // topK 做下限保护，避免传入 0 或负值导致底层行为不确定。
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder()
                .query(question)
                .topK(Math.max(1, topK));
        Filter.Expression scopeFilter = buildScopeFilter(scope);
        if (scopeFilter != null) {
            searchRequestBuilder.filterExpression(scopeFilter);
        }
        SearchRequest searchRequest = searchRequestBuilder.build();

        // scopeFilter 已包含授权后的 document/version 范围，底层只召回可问答向量。
        return vectorStore.similaritySearch(searchRequest).stream()
                .map(this::toRetrievedChunk)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 构造可问答版本范围过滤表达式。
     *
     * <p>每个 document 的可问答版本可能不同，因此必须构造成成对条件：
     * {@code (documentId = A AND version = 2) OR (documentId = B AND version = 4)}。</p>
     *
     * <p>PGVector 转换器不支持 {@code ISNULL}，因此初始版本的历史向量兼容通过
     * {@code splitVersion = v1} 表达，避免问答检索在底层过滤转换阶段抛出异常。</p>
     *
     * @param scope 可问答文档版本范围
     * @return 过滤表达式；scope 为空时返回 null
     */
    private static Filter.Expression buildScopeFilter(List<AskableDocumentVersion> scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> documentVersionFilters = new ArrayList<>();
        for (AskableDocumentVersion item : scope) {
            documentVersionFilters.add(builder.and(
                    builder.eq(METADATA_DOCUMENT_ID, item.documentId()),
                    builder.group(buildVersionFilter(builder, item.askableVersionNumber()))));
        }
        FilterExpressionBuilder.Op result = documentVersionFilters.getFirst();
        for (int i = 1; i < documentVersionFilters.size(); i++) {
            result = builder.or(result, documentVersionFilters.get(i));
        }
        return result.build();
    }

    /**
     * 构造单个文档版本的向量元数据过滤条件。
     *
     * @param builder 过滤表达式构造器
     * @param askableVersionNumber 当前可问答版本号
     * @return 可被 PGVector 转换器处理的版本过滤条件
     */
    private static FilterExpressionBuilder.Op buildVersionFilter(
            FilterExpressionBuilder builder,
            int askableVersionNumber) {
        FilterExpressionBuilder.Op versionFilter = builder.or(
                builder.eq(METADATA_DOCUMENT_VERSION_NUMBER, askableVersionNumber),
                builder.eq(METADATA_SPLIT_VERSION, "version-" + askableVersionNumber + "-v1"));
        if (askableVersionNumber == INITIAL_DOCUMENT_VERSION_NUMBER) {
            versionFilter = builder.or(versionFilter, builder.eq(METADATA_SPLIT_VERSION, LEGACY_INITIAL_SPLIT_VERSION));
        }
        return versionFilter;
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
        Integer sourceVersionNumber = resolveSourceVersionNumber(metadata);
        String sourceFilename = asString(metadata.get(METADATA_SOURCE_FILE));
        Instant sourceUpdatedAt = asInstant(metadata.get(METADATA_SOURCE_UPDATED_AT));
        // 向量文档正文允许为空，统一归一为空字符串，减少上层空值判断分支。
        String content = chunkDocument.getText() == null ? "" : chunkDocument.getText();
        return new RetrievedChunk(
                documentId,
                kbId,
                chunkIndex,
                content,
                sourceVersionNumber,
                sourceFilename,
                sourceUpdatedAt);
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

    /**
     * 将任意元数据对象安全转为可空整数。
     *
     * @param value 待转换元数据值
     * @return 解析得到的整数；输入为空或不可解析时返回 null
     */
    private static Integer asNullableInt(Object value) {
        int parsed = asInt(value, Integer.MIN_VALUE);
        return parsed == Integer.MIN_VALUE ? null : parsed;
    }

    /**
     * 将 ISO-8601 字符串元数据安全转为 Instant。
     *
     * @param value 待转换元数据值
     * @return 解析后的时间；输入为空或格式非法时返回 null
     */
    private static Instant asInstant(Object value) {
        String stringValue = asString(value);
        if (stringValue == null || stringValue.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(stringValue);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * 解析分块对应的文档版本号。
     *
     * <p>新向量优先读取显式的 {@code documentVersionNumber}。对于本次变更前写入的新版本向量，
     * 可从 {@code splitVersion=version-{versionNumber}-v1} 中反推文档版本号，避免历史可问答版本被过滤。</p>
     *
     * @param metadata 向量元数据
     * @return 文档版本号；无法解析时返回 null
     */
    private static Integer resolveSourceVersionNumber(Map<String, Object> metadata) {
        Integer explicitVersionNumber = asNullableInt(metadata.get(METADATA_DOCUMENT_VERSION_NUMBER));
        if (explicitVersionNumber != null) {
            return explicitVersionNumber;
        }
        return parseVersionNumberFromSplitVersion(asString(metadata.get(METADATA_SPLIT_VERSION)));
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
        return asNullableInt(remainder.substring(0, separatorIndex));
    }
}
