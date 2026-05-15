package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentStorageKeyResolverTest {

    @Test
    @DisplayName("artifact key 应包含 workspaceId、documentId、versionNumber 和 artifact 名称")
    void resolveArtifactKey_shouldContainVersionIdentityAndArtifactName() {
        DocumentStorageKeyResolver resolver = new DocumentStorageKeyResolver();

        String key = resolver.resolveArtifactKey("workspace-1", new DocumentId("doc-1"), 7, "cleaned.md");

        assertEquals("artifacts/workspace-1/documents/doc-1/versions/7/cleaned.md", key);
    }

    @Test
    @DisplayName("source key 与 artifact key 应使用隔离 prefix")
    void resolveKeys_shouldSeparateSourceAndArtifactsPrefix() {
        DocumentStorageKeyResolver resolver = new DocumentStorageKeyResolver();
        DocumentId documentId = new DocumentId("doc-2");

        String sourceKey = resolver.resolveSourceKey("workspace-1", documentId, 2, "a.pdf");
        String artifactKey = resolver.resolveArtifactKey("workspace-1", documentId, 2, "cleaned.md");

        assertEquals("source/workspace-1/documents/doc-2/versions/2/a.pdf", sourceKey);
        assertEquals("artifacts/workspace-1/documents/doc-2/versions/2/cleaned.md", artifactKey);
    }

    @Test
    @DisplayName("key 解析应清洗文件名路径分隔符")
    void resolveKeys_shouldSanitizeFilenameSeparators() {
        DocumentStorageKeyResolver resolver = new DocumentStorageKeyResolver();

        String key = resolver.resolveArtifactKey("workspace-1", new DocumentId("doc-3"), 1, "../cleaned.md");

        assertEquals("artifacts/workspace-1/documents/doc-3/versions/1/.._cleaned.md", key);
    }

    @Test
    @DisplayName("版本号小于 1 时应拒绝解析 key")
    void resolveKeys_shouldRejectInvalidVersionNumber() {
        DocumentStorageKeyResolver resolver = new DocumentStorageKeyResolver();

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveArtifactKey("workspace-1", new DocumentId("doc-4"), 0, "cleaned.md"));
    }
}
