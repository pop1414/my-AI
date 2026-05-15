package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentSourceStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("回退读取首个文件时应跳过 cleaned.md 等中间产物")
    void load_shouldIgnoreProcessingArtifacts_whenFallbackLoading() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-1");
        Path documentDirectory = tempDir.resolve("doc-source-1");
        Files.createDirectories(documentDirectory);
        Files.writeString(documentDirectory.resolve("cleaned.md"), "artifact", StandardCharsets.UTF_8);
        Files.writeString(documentDirectory.resolve("origin.txt"), "source-content", StandardCharsets.UTF_8);

        byte[] loaded = storage.load(documentId, "missing-name.txt").orElseThrow();

        assertArrayEquals("source-content".getBytes(StandardCharsets.UTF_8), loaded);
    }

    @Test
    @DisplayName("当目录仅剩中间产物时，回退加载应返回空")
    void load_shouldReturnEmpty_whenOnlyArtifactsRemain() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-2");
        Path documentDirectory = tempDir.resolve("doc-source-2");
        Files.createDirectories(documentDirectory);
        Files.writeString(documentDirectory.resolve("cleaned.md"), "artifact", StandardCharsets.UTF_8);
        Files.writeString(documentDirectory.resolve("parse-result.json"), "{}", StandardCharsets.UTF_8);

        assertFalse(storage.load(documentId, "missing-name.txt").isPresent());
    }

    @Test
    @DisplayName("版本源文件应按版本号隔离，允许不同版本使用相同文件名")
    void saveVersion_shouldIsolateContentByVersionNumber_whenFilenameIsSame() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-3");

        storage.saveVersion(documentId, 2, "same.pdf", "version-2".getBytes(StandardCharsets.UTF_8));
        storage.saveVersion(documentId, 3, "same.pdf", "version-3".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(
                "version-2".getBytes(StandardCharsets.UTF_8),
                storage.loadVersion(documentId, 2, "same.pdf").orElseThrow());
        assertArrayEquals(
                "version-3".getBytes(StandardCharsets.UTF_8),
                storage.loadVersion(documentId, 3, "same.pdf").orElseThrow());
    }

    @Test
    @DisplayName("同一版本同名源文件已存在但内容不一致时，应拒绝覆盖")
    void saveVersionIfAbsent_shouldRejectDifferentContent_whenSameVersionFileExists() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-4");

        boolean created = storage.saveVersionIfAbsent(
                documentId,
                4,
                "same.pdf",
                "first-content".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> storage.saveVersionIfAbsent(
                documentId,
                4,
                "same.pdf",
                "second-content".getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(
                "first-content".getBytes(StandardCharsets.UTF_8),
                storage.loadVersion(documentId, 4, "same.pdf").orElseThrow());
        org.junit.jupiter.api.Assertions.assertTrue(created);
    }

    @Test
    @DisplayName("同一版本同名源文件内容一致时，应返回幂等命中")
    void saveVersionIfAbsent_shouldReturnFalse_whenSameContentAlreadyExists() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-5");
        byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(storage.saveVersionIfAbsent(documentId, 5, "same.pdf", content));

        assertFalse(storage.saveVersionIfAbsent(documentId, 5, "same.pdf", content));
    }

}
