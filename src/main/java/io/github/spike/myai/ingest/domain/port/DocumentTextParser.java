package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentParseResult;

/**
 * 文档文本解析端口（Domain Port）。
 *
 * <p>该端口定义将原始文件字节解析为结构化中间产物的能力。
 * V1 版本升级：返回值从纯文本字符串改为 {@link DocumentParseResult}，
 * 使得下游可以同时获取 cleaned.md 主链产物和 processingMetadata。
 *
 * <p>解析链路：raw.xhtml → cleaned.html → cleaned.md，
 * 其中 cleaned.md 是后续分块和向量化的唯一输入来源。
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentTextParser {

    /**
     * 将原始文件字节解析为一期中间产物结果。
     *
     * <p>实现侧应完成以下步骤：
     * <ol>
     *   <li>调用 Tika 产出原始 XHTML；</li>
     *   <li>经 Jsoup 语义清洗产出 cleaned.html；</li>
     *   <li>经 flexmark 转换产出 cleaned.md；</li>
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

