package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.exception.DocumentSourceContentConflictException;
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
    @DisplayName("默认源文件读取应命中 source prefix 下的 version 1 路径")
    void load_shouldReadVersionOneFromSourcePrefix() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-1");

        storage.save(documentId, "origin.txt", "source-content".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(
                "source-content".getBytes(StandardCharsets.UTF_8),
                storage.load(documentId, "origin.txt").orElseThrow());
        assertTrue(Files.exists(tempDir
                .resolve("source")
                .resolve("default")
                .resolve("documents")
                .resolve("doc-source-1")
                .resolve("versions")
                .resolve("1")
                .resolve("origin.txt")));
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
        assertTrue(Files.exists(tempDir
                .resolve("source")
                .resolve("default")
                .resolve("documents")
                .resolve("doc-source-3")
                .resolve("versions")
                .resolve("2")
                .resolve("same.pdf")));
        assertFalse(Files.exists(tempDir.resolve("artifacts").resolve("default")));
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

        assertThrows(DocumentSourceContentConflictException.class, () -> storage.saveVersionIfAbsent(
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

    @Test
    @DisplayName("源文件缺失时不应回退到 document 级旧路径")
    void loadVersion_shouldReturnEmpty_whenVersionFileMissingButDocumentLevelFileExists() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentSourceStorage storage = new LocalDocumentSourceStorage(properties);
        DocumentId documentId = new DocumentId("doc-source-6");
        Path documentLevelDirectory = tempDir.resolve("doc-source-6");
        Files.createDirectories(documentLevelDirectory);
        Files.writeString(documentLevelDirectory.resolve("same.pdf"), "document-level-source", StandardCharsets.UTF_8);

        assertFalse(storage.load(documentId, "same.pdf").isPresent());
        assertFalse(storage.loadVersion(documentId, 1, "same.pdf").isPresent());
        assertFalse(storage.loadVersion(documentId, 2, "same.pdf").isPresent());
    }

}
