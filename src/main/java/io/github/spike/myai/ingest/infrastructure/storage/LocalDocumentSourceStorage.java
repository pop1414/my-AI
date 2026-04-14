package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 本地文件系统文档源存储实现。
 */
@Component
public class LocalDocumentSourceStorage implements DocumentSourceStorage {

    private final Path rootDirectory;

    public LocalDocumentSourceStorage(IngestProperties ingestProperties) {
        this.rootDirectory = Path.of(ingestProperties.getStorage().getRootDir());
    }

    @Override
    public void save(DocumentId documentId, String filename, byte[] content) {
        String safeFilename = sanitizeFilename(filename);
        Path filePath = resolveFilePath(documentId, safeFilename);
        try {
            // 目录结构：{root}/{documentId}/{filename}
            Files.createDirectories(filePath.getParent());
            // 幂等写入：已存在时不覆盖，保持首份受理内容稳定
            if (Files.notExists(filePath)) {
                Files.write(filePath, content, StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save source file", ex);
        }
    }

    @Override
    public Optional<byte[]> load(DocumentId documentId, String filename) {
        String safeFilename = sanitizeFilename(filename);
        Path filePath = resolveFilePath(documentId, safeFilename);
        try {
            // 优先按“documentId + filename”精确读取。
            if (Files.exists(filePath)) {
                return Optional.of(Files.readAllBytes(filePath));
            }

            // 兼容历史数据：若文件名不一致，回退读取该文档目录下首个文件。
            Path documentDirectory = rootDirectory.resolve(documentId.value());
            if (Files.notExists(documentDirectory) || !Files.isDirectory(documentDirectory)) {
                return Optional.empty();
            }
            try (var stream = Files.list(documentDirectory)) {
                Path firstFile = stream.filter(Files::isRegularFile).findFirst().orElse(null);
                if (firstFile == null) {
                    return Optional.empty();
                }
                return Optional.of(Files.readAllBytes(firstFile));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load source file", ex);
        }
    }

    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        Path documentDirectory = rootDirectory.resolve(documentId.value()).normalize();
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        Path normalizedTarget = documentDirectory.toAbsolutePath().normalize();
        // 防御性校验：确保删除目标始终在配置的 root 目录内。
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IllegalStateException("invalid source directory path");
        }
        if (Files.notExists(normalizedTarget)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(normalizedTarget)) {
            // 先删子文件再删目录，避免目录非空报错。
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

    private Path resolveFilePath(DocumentId documentId, String safeFilename) {
        // 统一路径拼装入口，避免业务层直接拼接本地路径。
        return rootDirectory.resolve(documentId.value()).resolve(safeFilename);
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "uploaded.bin";
        }
        // 去除路径分隔符，防止目录穿越与非法路径写入。
        // 名字清洗，防止黑客攻击（防止“目录穿越”攻击）
        String replaced = filename.replace('\\', '_').replace('/', '_').trim();
        if (replaced.isEmpty()) {
            return "uploaded.bin";
        }
        return replaced;
    }
}
