package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentProcessingArtifactStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应按版本级 artifacts prefix 写入 cleaned.md 和 parse-result.json，并按配置决定是否保留调试产物")
    void saveVersion_shouldWriteArtifactsUnderVersionArtifactsPrefix() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        properties.getStorage().getArtifacts().setKeepRawXhtml(true);
        properties.getStorage().getArtifacts().setKeepCleanedHtml(false);
        properties.getStorage().getArtifacts().setKeepParseResultJson(true);
        LocalDocumentProcessingArtifactStorage storage = new LocalDocumentProcessingArtifactStorage(properties);

        DocumentId documentId = new DocumentId("doc-artifact-1");
        DocumentParseResult parseResult = new DocumentParseResult(
                "<html><body>raw</body></html>",
                "<p>cleaned</p>",
                "# 标题",
                "{\"schema_version\":\"v1\"}");

        storage.saveVersion("workspace-1", documentId, 2, parseResult);

        Path artifactDirectory = tempDir
                .resolve("artifacts")
                .resolve("workspace-1")
                .resolve("documents")
                .resolve("doc-artifact-1")
                .resolve("versions")
                .resolve("2");
        assertTrue(Files.exists(artifactDirectory.resolve("cleaned.md")));
        assertTrue(Files.exists(artifactDirectory.resolve("raw.xhtml")));
        assertFalse(Files.exists(artifactDirectory.resolve("cleaned.html")));
        assertTrue(Files.exists(artifactDirectory.resolve("parse-result.json")));
        assertEquals("# 标题", Files.readString(artifactDirectory.resolve("cleaned.md")));
        assertFalse(Files.exists(tempDir.resolve("source").resolve("workspace-1")));
    }

    @Test
    @DisplayName("读取版本 artifact 时应返回逻辑 key、正文和字节长度")
    void loadVersionArtifact_shouldReturnContentWithStableKey() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentProcessingArtifactStorage storage = new LocalDocumentProcessingArtifactStorage(properties);
        DocumentId documentId = new DocumentId("doc-artifact-2");
        DocumentParseResult parseResult = new DocumentParseResult("raw", "html", "hello markdown", "{}");

        storage.saveVersion("workspace-1", documentId, 3, parseResult);

        DocumentVersionArtifactContent content = storage
                .loadVersionArtifact(
                        "workspace-1",
                        documentId,
                        3,
                        DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                        1024)
                .orElseThrow();

        assertEquals(
                "artifacts/workspace-1/documents/doc-artifact-2/versions/3/cleaned.md",
                content.key());
        assertEquals("hello markdown", content.content());
        assertEquals("hello markdown".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, content.contentLength());
    }

    @Test
    @DisplayName("artifact 缺失时应返回空且不读取 source prefix 下的同名文件")
    void loadVersionArtifact_shouldReturnEmpty_whenArtifactMissingEvenIfSourceHasSameName() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentProcessingArtifactStorage storage = new LocalDocumentProcessingArtifactStorage(properties);
        DocumentId documentId = new DocumentId("doc-artifact-3");
        Path sourceDirectory = tempDir
                .resolve("source")
                .resolve("workspace-1")
                .resolve("documents")
                .resolve("doc-artifact-3")
                .resolve("versions")
                .resolve("4");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("cleaned.md"), "source should not be used");

        Optional<DocumentVersionArtifactContent> content = storage.loadVersionArtifact(
                "workspace-1",
                documentId,
                4,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024);

        assertFalse(content.isPresent());
    }

    @Test
    @DisplayName("artifact 超过最大读取字节数时应抛出稳定异常")
    void loadVersionArtifact_shouldRejectContentLargerThanMaxBytes() {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentProcessingArtifactStorage storage = new LocalDocumentProcessingArtifactStorage(properties);
        DocumentId documentId = new DocumentId("doc-artifact-4");
        storage.saveVersion("workspace-1", documentId, 5, new DocumentParseResult("raw", "html", "123456", "{}"));

        DocumentVersionArtifactTooLargeException exception = assertThrows(
                DocumentVersionArtifactTooLargeException.class,
                () -> storage.loadVersionArtifact(
                        "workspace-1",
                        documentId,
                        5,
                        DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                        5));

        assertEquals(6, exception.contentLength());
        assertEquals(5, exception.maxBytes());
    }

    @Test
    @DisplayName("删除文档 artifact 时只应清理 artifacts prefix，不应影响 source prefix")
    void deleteByDocumentId_shouldDeleteOnlyArtifactsPrefix() throws Exception {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().setRootDir(tempDir.toString());
        LocalDocumentProcessingArtifactStorage storage = new LocalDocumentProcessingArtifactStorage(properties);
        DocumentId documentId = new DocumentId("doc-artifact-delete");
        storage.saveVersion("workspace-1", documentId, 1, new DocumentParseResult("raw", "html", "content", "{}"));
        Path sourceDirectory = tempDir
                .resolve("source")
                .resolve("workspace-1")
                .resolve("documents")
                .resolve("doc-artifact-delete")
                .resolve("versions")
                .resolve("1");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("origin.pdf"), "source");

        storage.deleteByDocumentId("workspace-1", documentId);

        assertFalse(Files.exists(tempDir
                .resolve("artifacts")
                .resolve("workspace-1")
                .resolve("documents")
                .resolve("doc-artifact-delete")));
        assertTrue(Files.exists(sourceDirectory.resolve("origin.pdf")));
    }
}
