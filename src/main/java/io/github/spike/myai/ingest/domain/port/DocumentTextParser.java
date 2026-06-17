package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentParseResult;

/**
 * 文档文本解析端口（Domain Port）。
 *
 * <p>该端口定义将原始文件字节解析为结构化中间产物的能力。
 * 返回 {@link DocumentParseResult}，包含 cleanedMarkdown 主链产物和 processingMetadata。
 *
 * <p>解析链路：原始文件 → cleaned.md，
 * 其中 cleaned.md 是后续分块和向量化的唯一输入来源。
 *
 * <p>当前实现：
 * <ul>
 *   <li>{@code DoclingDocumentParser} — 通过 Docling Serve 的 HybridChunker 一步完成转换与分块。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentTextParser {

    /**
     * 将原始文件字节解析为结构化中间产物。
     *
     * <p>实现侧应完成以下步骤：
     * <ol>
     *   <li>识别文档格式并调用相应解析引擎；</li>
     *   <li>执行必要的文本清洗（换行符统一、控制字符移除等）；</li>
     *   <li>产出 cleanedMarkdown 主链产物；</li>
     *   <li>从文件元数据与 Markdown 内容中提取 processingMetadata。</li>
     * </ol>
     *
     * @param filename 原始文件名，用于识别文档类型和提取元数据
     * @param content  文件字节数组，不可为空
     * @return 解析后的中间产物结果，cleanedMarkdown 不可为空
     * @throws IllegalStateException 当内容为空或解析失败时
     */
    DocumentParseResult parse(String filename, byte[] content);
}

