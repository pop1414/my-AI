package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;

/**
 * 文档处理中间产物存储端口（Domain Port）。
 *
 * <p>该端口定义处理链路中产生的中间产物（如 cleaned.md、raw.xhtml 等）
 * 的持久化能力。领域层只声明"需要保存解析产物"，不关心底层是本地文件系统、
 * 对象存储还是其他实现。
 *
 * <p>设计约定：
 * <ul>
 *   <li>cleaned.md 为强制写入的主链产物，不可跳过；</li>
 *   <li>raw.xhtml / cleaned.html / parse-result.json 为可选调试产物，
 *       由配置开关控制是否保留。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentProcessingArtifactStorage {

    /**
     * 保存文档解析产物到存储介质。
     *
     * <p>实现侧应确保：
     * <ol>
     *   <li>cleaned.md 强制写入，无论配置如何；</li>
     *   <li>调试产物按配置决定是否保留；</li>
     *   <li>写入失败时抛出明确的运行时异常。</li>
     * </ol>
     *
     * @param documentId  文档资产 ID，用于定位存储路径
     * @param parseResult 文档解析结果（含所有中间产物）
     */
    void save(DocumentId documentId, DocumentParseResult parseResult);
}
