package io.github.spike.myai.ingest.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * chunk 结构化元数据。
 *
 * <p>替代原有的 {@link SourceHint}，由 Docling Serve 的 HybridChunker 在 server-side
 * 产出后映射到此值对象。携带面包屑标题链、源页码和内容类型三个结构化信号，
 * 供下游向量索引和检索使用。
 *
 * <p>TXT 等无结构标记的格式：headings 为空 list、pageNumber 为 0、contentType 为
 * {@link ChunkContentType#PARAGRAPH}，下游代码不可对这三个字段做非空断言。
 *
 * @param headings     面包屑标题链（不可变，null 归一化为空 list）
 * @param pageNumber   源页码（0 表示未知）
 * @param contentType  内容类型（null 归一化为 {@link ChunkContentType#PARAGRAPH}）
 * @author spike
 * @since 1.0.0
 */
public record ChunkMetadata(
        List<String> headings,
        int pageNumber,
        ChunkContentType contentType) {

    /**
     * 紧凑构造函数：防御性拷贝 + 空值归一化。
     */
    public ChunkMetadata {
        // headings: null → 空不可变 list；非 null → 防御性拷贝为不可变 list
        if (headings == null) {
            headings = Collections.emptyList();
        } else {
            headings = Collections.unmodifiableList(new ArrayList<>(headings));
        }

        // pageNumber: 负数不合法（0 表示未知，正数表示实际页码）
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative, got: " + pageNumber);
        }

        // contentType: null → 默认 PARAGRAPH
        if (contentType == null) {
            contentType = ChunkContentType.PARAGRAPH;
        }
    }
}
