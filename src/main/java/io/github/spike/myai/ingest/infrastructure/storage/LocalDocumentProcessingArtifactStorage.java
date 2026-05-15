package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.application.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 本地文件系统文档处理中间产物存储实现。
 *
 * <p>基于 {@link DocumentProcessingArtifactStorage} 端口规范，将文档解析链路的中间产物
 * 持久化到本地文件系统。版本级存储路径结构由 {@link DocumentStorageKeyResolver} 统一生成，
 * 当前格式为：{@code {rootDir}/artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/}。
 *
 * <p>写入策略：
 * <ul>
 *   <li><b>cleaned.md</b>：主链产物，<em>强制写入</em>，不受配置控制；</li>
 *   <li><b>raw.xhtml</b>：Tika 原始输出，受 {@code keepRawXhtml} 控制；</li>
 *   <li><b>cleaned.html</b>：Jsoup 清洗后 HTML，受 {@code keepCleanedHtml} 控制；</li>
 *   <li><b>parse-result.json</b>：processingMetadata 文件化载体，受 {@code keepParseResultJson} 控制。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Component
public class LocalDocumentProcessingArtifactStorage implements DocumentProcessingArtifactStorage {

    /** Tika 原始 XHTML 输出文件名 */
    static final String RAW_XHTML_FILENAME = "raw.xhtml";
    /** Jsoup 语义清洗后的 HTML 文件名 */
    static final String CLEANED_HTML_FILENAME = "cleaned.html";
    /** 主链产物 cleaned.md 文件名（强制写入） */
    static final String CLEANED_MARKDOWN_FILENAME = "cleaned.md";
    /** processingMetadata 序列化 JSON 文件名 */
    static final String PARSE_RESULT_FILENAME = "parse-result.json";

    /** 文件存储根目录路径 */
    private final Path rootDirectory;
    /** 源文件与处理产物逻辑 key 解析器 */
    private final DocumentStorageKeyResolver keyResolver = new DocumentStorageKeyResolver();
    /** 是否保留 Tika 原始 XHTML（调试用） */
    private final boolean keepRawXhtml;
    /** 是否保留 Jsoup 清洗后的 HTML（调试用） */
    private final boolean keepCleanedHtml;
    /** 是否保留 processingMetadata 的 JSON 文件载体 */
    private final boolean keepParseResultJson;

    /**
     * 构造器注入：从配置属性中读取存储路径和产物保留策略。
     *
     * @param ingestProperties ingest 管道配置属性
     */
    public LocalDocumentProcessingArtifactStorage(IngestProperties ingestProperties) {
        this.rootDirectory = Path.of(ingestProperties.getStorage().getRootDir());
        this.keepRawXhtml = ingestProperties.getStorage().getArtifacts().isKeepRawXhtml();
        this.keepCleanedHtml = ingestProperties.getStorage().getArtifacts().isKeepCleanedHtml();
        this.keepParseResultJson = ingestProperties.getStorage().getArtifacts().isKeepParseResultJson();
    }

    /**
     * 保存文档解析产物到本地文件系统。
     *
     * <p>写入顺序：
     * <ol>
     *   <li>先确保文档目录存在；</li>
     *   <li><b>强制写入</b> cleaned.md（主链产物）；</li>
     *   <li>按配置开关依次写入可选调试产物。</li>
     * </ol>
     *
     * <p>每次写入使用 TRUNCATE_EXISTING 模式，确保每次重试都是全量覆盖，
     * 避免前次处理残留内容污染当前结果。
     *
     * @param documentId  文档资产 ID
     * @param parseResult 文档解析结果
     */
    @Override
    public void save(DocumentId documentId, DocumentParseResult parseResult) {
        saveVersion(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId, 1, parseResult);
    }

    /**
     * 保存指定版本的文档解析产物到本地文件系统。
     *
     * <p>所有产物均写入 artifacts prefix 下的版本目录，避免与 source prefix 下的源文件混放。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param parseResult 文档解析结果
     */
    @Override
    public void saveVersion(String workspaceId, DocumentId documentId, int versionNumber, DocumentParseResult parseResult) {
        Path artifactDirectory = resolveArtifactDirectory(workspaceId, documentId, versionNumber);
        try {
            // 确保版本级 artifacts 目录存在（已存在时静默跳过）
            Files.createDirectories(artifactDirectory);
            // 强制写入主链产物 cleaned.md，不受任何配置开关控制
            writeText(artifactDirectory.resolve(CLEANED_MARKDOWN_FILENAME), parseResult.cleanedMarkdown());
            // 可选写入：Tika 原始 XHTML
            if (keepRawXhtml && parseResult.rawXhtml() != null && !parseResult.rawXhtml().isBlank()) {
                writeText(artifactDirectory.resolve(RAW_XHTML_FILENAME), parseResult.rawXhtml());
            }
            // 可选写入：Jsoup 清洗后的 HTML
            if (keepCleanedHtml && parseResult.cleanedHtml() != null && !parseResult.cleanedHtml().isBlank()) {
                writeText(artifactDirectory.resolve(CLEANED_HTML_FILENAME), parseResult.cleanedHtml());
            }
            // 可选写入：processingMetadata JSON
            if (keepParseResultJson
                    && parseResult.processingMetadata() != null
                    && !parseResult.processingMetadata().isBlank()) {
                writeText(artifactDirectory.resolve(PARSE_RESULT_FILENAME), parseResult.processingMetadata());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save processing artifacts", ex);
        }
    }

    /**
     * 读取指定版本的处理产物。
     *
     * <p>缺失产物返回空，不尝试读取源文件、不触发重新解析，也不从向量分块拼接正文。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param artifactName 产物名称
     * @param maxBytes 最大读取字节数
     * @return 产物正文，未命中时为空
     */
    @Override
    public Optional<DocumentVersionArtifactContent> loadVersionArtifact(
            String workspaceId,
            DocumentId documentId,
            int versionNumber,
            String artifactName,
            long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        String artifactKey = keyResolver.resolveArtifactKey(workspaceId, documentId, versionNumber, artifactName);
        Path artifactPath = resolveKeyPath(artifactKey);
        try {
            if (Files.notExists(artifactPath) || !Files.isRegularFile(artifactPath)) {
                return Optional.empty();
            }
            long contentLength = Files.size(artifactPath);
            if (contentLength > maxBytes) {
                throw new DocumentVersionArtifactTooLargeException(contentLength, maxBytes);
            }
            return Optional.of(new DocumentVersionArtifactContent(
                    artifactKey,
                    Files.readString(artifactPath, StandardCharsets.UTF_8),
                    contentLength));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load processing artifact", ex);
        }
    }

    private Path resolveArtifactDirectory(String workspaceId, DocumentId documentId, int versionNumber) {
        String artifactKey = keyResolver.resolveArtifactKey(
                workspaceId,
                documentId,
                versionNumber,
                CLEANED_MARKDOWN_FILENAME);
        return resolveKeyPath(artifactKey).getParent();
    }

    private Path resolveKeyPath(String key) {
        return rootDirectory.resolve(Path.of(key)).normalize();
    }

    /**
     * 以 UTF-8 编码写入文本内容到文件。
     *
     * <p>使用 {@link StandardOpenOption#TRUNCATE_EXISTING} 确保每次写入都是全量覆盖，
     * {@link StandardOpenOption#CREATE} 确保文件不存在时自动创建。
     *
     * @param path    目标文件路径
     * @param content 文本内容
     * @throws IOException 写入失败时抛出
     */
    private static void writeText(Path path, String content) throws IOException {
        Files.writeString(
                path,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }
}
