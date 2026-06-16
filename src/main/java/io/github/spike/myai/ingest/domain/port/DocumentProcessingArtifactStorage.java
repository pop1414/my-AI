package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.util.Optional;

/**
 * 文档处理中间产物存储端口（Domain Port）。
 *
 * <p>该端口定义处理链路中产生的中间产物（如 cleaned.md、parse-result.json 等）
 * 的持久化能力。领域层只声明"需要保存解析产物"，不关心底层是本地文件系统、
 * 对象存储还是其他实现。
 *
 * <p>设计约定：
 * <ul>
 *   <li>cleaned.md 为强制写入的主链产物，不可跳过；</li>
 *   <li>parse-result.json 为可选调试产物，由配置开关控制是否保留。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentProcessingArtifactStorage {

    /** 主链正文处理产物名称。 */
    String CLEANED_MARKDOWN_ARTIFACT_NAME = "cleaned.md";

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
    default void save(DocumentId documentId, DocumentParseResult parseResult) {
        saveVersion(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId, 1, parseResult);
    }

    /**
     * 保存指定版本的文档解析产物到存储介质。
     *
     * <p>版本级 artifact key 必须包含 workspaceId、documentId、versionNumber 和 artifact 名称，
     * 避免不同版本复用同一份 cleaned.md。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param parseResult 文档解析结果（含所有中间产物）
     */
    void saveVersion(String workspaceId, DocumentId documentId, int versionNumber, DocumentParseResult parseResult);

    /**
     * 读取指定版本的处理产物。
     *
     * <p>缺失产物返回 {@link Optional#empty()}，作为稳定业务分支供上层映射
     * {@code CONTENT_ARTIFACT_MISSING}；产物过大时抛出
     * {@link DocumentVersionArtifactTooLargeException}。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param artifactName 产物名称
     * @param maxBytes 允许读取的最大字节数
     * @return 产物内容，未命中时为空
     */
    Optional<DocumentVersionArtifactContent> loadVersionArtifact(
            String workspaceId,
            DocumentId documentId,
            int versionNumber,
            String artifactName,
            long maxBytes);

    /**
     * 删除文档资产对应的全部处理产物。
     *
     * <p>删除文档时需要同步清理 artifacts prefix 下的正文产物，
     * 避免源文件已删除但 cleaned.md 仍可被路径命中。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     */
    void deleteByDocumentId(String workspaceId, DocumentId documentId);
}
