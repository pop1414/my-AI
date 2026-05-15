package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 本地文件系统文档源存储实现。
 *
 * <p>基于 {@link DocumentSourceStorage} 端口规范，提供原始上传文件在本地文件系统上的
 * 存取与删除能力。版本化源文件路径由 {@link DocumentStorageKeyResolver} 统一生成，
 * 当前格式为：{@code {rootDir}/source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}}。
 *
 * <p>设计要点：
 * <ul>
 *   <li>写入幂等：已存在文件不覆盖，保持首次受理内容稳定；</li>
 *   <li>文件名清洗：去除路径分隔符防止目录穿越攻击；</li>
 *   <li>读取兼容：精确文件名未命中时，回退读取文档目录下首个非中间产物文件；</li>
 *   <li>安全删除：确保删除目标在配置的 root 目录内，防止越权删除。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Component
public class LocalDocumentSourceStorage implements DocumentSourceStorage {

    /**
     * 保留的中间产物文件名集合。
     * 在回退读取（按目录首个文件）时，需排除这些文件以免误将中间产物当作源文件返回。
     * 包括：raw.xhtml、cleaned.html、cleaned.md、parse-result.json
     */
    private static final Set<String> RESERVED_ARTIFACT_FILENAMES = Set.of(
            LocalDocumentProcessingArtifactStorage.RAW_XHTML_FILENAME,
            LocalDocumentProcessingArtifactStorage.CLEANED_HTML_FILENAME,
            LocalDocumentProcessingArtifactStorage.CLEANED_MARKDOWN_FILENAME,
            LocalDocumentProcessingArtifactStorage.PARSE_RESULT_FILENAME);

    /** 文件存储根目录路径 */
    private final Path rootDirectory;
    /** 源文件与处理产物逻辑 key 解析器 */
    private final DocumentStorageKeyResolver keyResolver = new DocumentStorageKeyResolver();

    /**
     * 构造器注入：从配置属性中读取存储根目录路径。
     *
     * @param ingestProperties ingest 管道配置属性
     */
    public LocalDocumentSourceStorage(IngestProperties ingestProperties) {
        this.rootDirectory = Path.of(ingestProperties.getStorage().getRootDir());
    }

    /**
     * 保存原始上传文件到本地文件系统。
     *
     * <p>实现细节：
     * <ol>
     *   <li>对文件名进行安全清洗（去除路径分隔符）；</li>
     *   <li>创建目录结构 {@code {root}/{documentId}/}；</li>
     *   <li>幂等写入：文件已存在时静默跳过，不覆盖已有内容。</li>
     * </ol>
     *
     * @param documentId 文档资产 ID
     * @param filename   原始文件名
     * @param content    文件字节内容
     */
    @Override
    public void save(DocumentId documentId, String filename, byte[] content) {
        Path filePath = resolveVersionFilePath(documentId, 1, filename);
        try {
            // 目录结构：{root}/source/{workspaceId}/documents/{documentId}/versions/1/{filename}
            Files.createDirectories(filePath.getParent());
            // 幂等写入：已存在时不覆盖，保持首份受理内容稳定
            if (Files.notExists(filePath)) {
                Files.write(filePath, content, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save source file", ex);
        }
    }

    /**
     * 保存指定版本的源文件到本地文件系统。
     *
     * <p>实现与 {@link #save(DocumentId, String, byte[])} 一致，
     * 区别在于存储路径携带版本号子目录：
     * {@code {root}/{documentId}/versions/{versionNumber}/{safeFilename}}。
     *
     * @param documentId   文档资产 ID
     * @param versionNumber 版本号
     * @param filename     原始文件名
     * @param content      文件字节内容
     */
    @Override
    public void saveVersion(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        saveVersionIfAbsent(documentId, versionNumber, filename, content);
    }

    /**
     * 幂等保存指定版本的源文件，并区分本次是否真正创建文件。
     *
     * <p>如果目标版本文件已存在，会读取已有内容与本次内容做字节级比较：
     * 内容一致时视为幂等命中并返回 {@code false}；内容不一致时说明出现并发候选版本冲突，
     * 直接抛出异常，避免同一版本号路径被错误内容污染。
     *
     * @param documentId   文档资产 ID
     * @param versionNumber 版本号
     * @param filename     原始文件名
     * @param content      文件字节内容
     * @return 本次调用是否创建了新文件
     */
    @Override
    public boolean saveVersionIfAbsent(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        Path filePath = resolveVersionFilePath(documentId, versionNumber, filename);
        try {
            Files.createDirectories(filePath.getParent());
            if (Files.exists(filePath)) {
                byte[] existingContent = Files.readAllBytes(filePath);
                if (!Arrays.equals(existingContent, content)) {
                    throw new IllegalStateException(VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE);
                }
                return false;
            }
            Files.write(filePath, content, StandardOpenOption.CREATE_NEW);
            return true;
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save version source file", ex);
        }
    }

    @Override
    public Optional<byte[]> load(DocumentId documentId, String filename) {
        Path filePath = resolveVersionFilePath(documentId, 1, filename);
        try {
            // 优先按 source prefix 下的 version 1 文件精确读取。
            if (Files.exists(filePath)) {
                return Optional.of(Files.readAllBytes(filePath));
            }

            Optional<byte[]> legacyExact = loadLegacyDocumentFile(documentId, filename);
            if (legacyExact.isPresent()) {
                return legacyExact;
            }

            // 兼容历史数据：若文件名不一致，回退读取该文档目录下首个文件。
            Path documentDirectory = rootDirectory.resolve(documentId.value());
            if (Files.notExists(documentDirectory) || !Files.isDirectory(documentDirectory)) {
                return Optional.empty();
            }
            try (var stream = Files.list(documentDirectory)) {
                Path firstFile = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !RESERVED_ARTIFACT_FILENAMES.contains(path.getFileName().toString()))
                        .findFirst()
                        .orElse(null);
                if (firstFile == null) {
                    return Optional.empty();
                }
                return Optional.of(Files.readAllBytes(firstFile));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load source file", ex);
        }
    }

    /**
     * 读取指定版本的源文件。
     *
     * <p>优先按版本路径精确读取；若版本路径下无文件，
     * 则回退到 {@link #load(DocumentId, String)} 兼容历史数据。
     *
     * @param documentId   文档资产 ID
     * @param versionNumber 版本号
     * @param filename     原始文件名
     * @return 文件字节数组，未命中时返回空
     */
    @Override
    public Optional<byte[]> loadVersion(DocumentId documentId, int versionNumber, String filename) {
        Path filePath = resolveVersionFilePath(documentId, versionNumber, filename);
        try {
            if (Files.exists(filePath)) {
                return Optional.of(Files.readAllBytes(filePath));
            }
            Path legacyVersionFilePath = resolveLegacyVersionFilePath(documentId, versionNumber, sanitizeFilename(filename));
            if (Files.exists(legacyVersionFilePath)) {
                return Optional.of(Files.readAllBytes(legacyVersionFilePath));
            }
            return load(documentId, filename);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load version source file", ex);
        }
    }

    /**
     * 按文档 ID 删除该文档目录下的所有文件。
     *
     * <p>安全机制：
     * <ol>
     *   <li>路径归一化后校验：确保删除目标始终在配置的 root 目录内（防越权删除）；</li>
     *   <li>目录不存在时静默返回；</li>
     *   <li>按深度倒序遍历（先删子文件再删目录），避免目录非空报错。</li>
     * </ol>
     *
     * @param documentId 文档资产 ID
     */
    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        // 路径归一化以防止路径遍历绕过安全校验
        Path documentDirectory = rootDirectory
                .resolve(DocumentStorageKeyResolver.SOURCE_PREFIX)
                .resolve(WorkspaceConstants.DEFAULT_WORKSPACE_ID)
                .resolve("documents")
                .resolve(documentId.value())
                .normalize();
        Path legacyDocumentDirectory = rootDirectory.resolve(documentId.value()).normalize();
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        Path normalizedTarget = documentDirectory.toAbsolutePath().normalize();
        Path normalizedLegacyTarget = legacyDocumentDirectory.toAbsolutePath().normalize();
        // 防御性校验：确保删除目标始终在配置的 root 目录内
        if (!normalizedTarget.startsWith(normalizedRoot) || !normalizedLegacyTarget.startsWith(normalizedRoot)) {
            throw new IllegalStateException("invalid source directory path");
        }
        deleteDirectoryIfExists(normalizedTarget);
        deleteDirectoryIfExists(normalizedLegacyTarget);
    }

    /**
     * 统一路径拼装入口。
     *
     * <p>统一构造 {@code {root}/{documentId}/{safeFilename}} 格式的路径，
     * 避免业务层直接拼接本地路径导致不一致。
     *
     * @param documentId   文档资产 ID
     * @param safeFilename 已清洗安全的文件名
     * @return 最终文件路径
     */
    private Optional<byte[]> loadLegacyDocumentFile(DocumentId documentId, String filename) throws IOException {
        Path legacyFilePath = rootDirectory.resolve(documentId.value()).resolve(sanitizeFilename(filename));
        if (Files.exists(legacyFilePath)) {
            return Optional.of(Files.readAllBytes(legacyFilePath));
        }
        return Optional.empty();
    }

    /**
     * 组装版本化源文件的本地存储路径。
     *
     * <p>路径格式：{@code {root}/{documentId}/versions/{versionNumber}/{safeFilename}}。
     *
     * @param documentId   文档资产 ID
     * @param versionNumber 版本号
     * @param safeFilename  已清洗的安全文件名
     * @return 版本化文件的完整路径
     */
    private Path resolveVersionFilePath(DocumentId documentId, int versionNumber, String safeFilename) {
        String sourceKey = keyResolver.resolveSourceKey(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                documentId,
                versionNumber,
                safeFilename);
        return rootDirectory.resolve(Path.of(sourceKey)).normalize();
    }

    private Path resolveLegacyVersionFilePath(DocumentId documentId, int versionNumber, String safeFilename) {
        return rootDirectory
                .resolve(documentId.value())
                .resolve("versions")
                .resolve(Integer.toString(versionNumber))
                .resolve(safeFilename);
    }

    private static void deleteDirectoryIfExists(Path directory) {
        if (Files.notExists(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            // 按路径深度倒序排序：先删子文件再删父目录，避免目录非空报错
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("failed to delete source file", ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete source directory", ex);
        }
    }

    /**
     * 文件名安全清洗。
     *
     * <p>目的：防止目录穿越攻击（Path Traversal）。
     * 将路径分隔符 {@code \} 和 {@code /} 替换为下划线，
     * 同时处理空文件名和全空白文件名的边界情况。
     *
     * @param filename 原始文件名（可能来自用户上传）
     * @return 清洗后的安全文件名
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "uploaded.bin";
        }
        // 去除路径分隔符，防止目录穿越与非法路径写入
        String replaced = filename.replace('\\', '_').replace('/', '_').trim();
        if (replaced.isEmpty()) {
            return "uploaded.bin";
        }
        return replaced;
    }
}
