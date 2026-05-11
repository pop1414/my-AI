package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentProcessingArtifactStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("应强制写入 cleaned.md 和 parse-result.json，并按配置决定是否保留调试产物")
    void save_shouldWriteArtifacts() throws Exception {
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

        storage.save(documentId, parseResult);

        Path documentDirectory = tempDir.resolve("doc-artifact-1");
        assertTrue(Files.exists(documentDirectory.resolve("cleaned.md")));
        assertTrue(Files.exists(documentDirectory.resolve("raw.xhtml")));
        assertFalse(Files.exists(documentDirectory.resolve("cleaned.html")));
        assertTrue(Files.exists(documentDirectory.resolve("parse-result.json")));
        assertEquals("# 标题", Files.readString(documentDirectory.resolve("cleaned.md")));
    }
}
