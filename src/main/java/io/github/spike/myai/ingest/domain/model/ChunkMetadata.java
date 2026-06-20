package io.github.spike.myai.ingest.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * chunk 结构化元数据。
 *
 * <p>由 Docling Serve 的 HybridChunker 在 server-side
 * 产出后映射到此值对象。携带面包屑标题链、源页码和内容类型三个结构化信号，
 * 供下游向量索引和检索使用。
 *
 * <p>TXT 等无结构标记的格式：headings 为空 list、pageNumber 为 0、contentType 为
 * {@link ChunkContentType#PARAGRAPH}，下游代码不可假定三个字段为非空/非默认值。
 *
 * <p>直接构造要求所有字段非 null；外部输入（如 Docling Serve 响应映射）应使用
 * {@link #of(List, int, ChunkContentType)} 安全构造，该工厂方法会归一化 null 和过滤非法元素。
 *
 * @param headings   面包屑标题链（不可变，禁止 null）
 * @param pageNumber 源页码（0 表示未知，禁止负数）
 * @param contentType 内容类型（禁止 null）
 * @throws NullPointerException 当 headings 或 contentType 为 null 时
 * @throws IllegalArgumentException 当 pageNumber 为负数时
 * @author spike
 * @since 1.0.0
 */
public record ChunkMetadata(
        List<String> headings,
        int pageNumber,
        ChunkContentType contentType) {

    /**
     * 紧凑构造函数：校验 + 防御性拷贝，不做默认值填充。
     */
    public ChunkMetadata {
        Objects.requireNonNull(headings, "headings must not be null");
        headings = Collections.unmodifiableList(new ArrayList<>(headings));

        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative, got: " + pageNumber);
        }

        Objects.requireNonNull(contentType, "contentType must not be null");
    }

    /**
     * 安全构造工厂：归一化外部输入的缺失值并过滤非法元素。
     *
     * <p>与直接构造的区别：
     * <ul>
     *   <li>{@code headings} 为 null → 空不可变 list；非 null → 过滤 null 和空白字符串后防御性拷贝</li>
     *   <li>{@code pageNumber} 仍拒绝负数（非法输入，不归一化）</li>
     *   <li>{@code contentType} 为 null → {@link ChunkContentType#PARAGRAPH}</li>
     * </ul>
     *
     * @throws IllegalArgumentException 当 pageNumber 为负数时
     */
    public static ChunkMetadata of(List<String> headings, int pageNumber, ChunkContentType contentType) {
        List<String> normalizedHeadings;
        if (headings == null) {
            normalizedHeadings = List.of();
        } else {
            normalizedHeadings = headings.stream()
                    .filter(Objects::nonNull)
                    .filter(h -> !h.isBlank())
                    .collect(Collectors.toUnmodifiableList());
        }

        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative, got: " + pageNumber);
        }

        ChunkContentType normalizedType = contentType != null ? contentType : ChunkContentType.PARAGRAPH;

        return new ChunkMetadata(normalizedHeadings, pageNumber, normalizedType);
    }
}
