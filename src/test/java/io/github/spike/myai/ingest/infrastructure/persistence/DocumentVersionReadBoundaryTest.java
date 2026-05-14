package io.github.spike.myai.ingest.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentVersionReadBoundaryTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");

    @Test
    @DisplayName("文档上传幂等读路径不得再从主表旧 file_hash/status 字段读取版本事实")
    void documentRepository_shouldReadVersionFactsFromVersionTable() throws IOException {
        String source = readSource(JdbcDocumentRepository.class);

        assertTrue(source.contains("v.file_hash = ?"));
        assertTrue(source.contains("d.latest_status <> 'DELETED'"));
        assertFalse(source.contains("d.latest_status NOT IN ('DELETING', 'DELETED')"));
        assertFalse(source.contains("d.file_hash = ?"));
        assertFalse(source.contains("d.status <> 'DELETED'"));
    }

    @Test
    @DisplayName("文档列表读模型应只读取 latest projection 或 version 表事实")
    void documentListRepository_shouldReadLatestProjectionAndVersionFacts() throws IOException {
        String source = readSource(JdbcDocumentListRepository.class);

        assertTrue(source.contains("d.latest_filename AS filename"));
        assertTrue(source.contains("v.file_size"));
        assertTrue(source.contains("d.latest_status AS status"));
        assertFalse(source.contains("d.filename AS filename"));
        assertFalse(source.contains("d.file_size"));
        assertFalse(source.contains("d.status AS status"));
    }

    @Test
    @DisplayName("版本历史读路径应按版本号倒序读取版本事实且不持久化 askable")
    void documentVersionHistoryRepository_shouldReadVersionFactsInStableOrder() throws IOException {
        String source = readSource(JdbcDocumentVersionHistoryRepository.class);

        assertTrue(source.contains("JOIN ingest_document_versions v"));
        assertTrue(source.contains("d.workspace_id = ?"));
        assertTrue(source.contains("ORDER BY v.version_number DESC"));
        assertFalse(source.contains("is_askable"));
        assertFalse(source.contains("askable"));
        assertFalse(source.contains("UPDATE ingest_documents"));
        assertFalse(source.contains("UPDATE ingest_document_versions"));
    }

    private static String readSource(Class<?> type) throws IOException {
        Path sourcePath = MAIN_SOURCE_ROOT.resolve(type.getName().replace('.', '/') + ".java");
        return Files.readString(sourcePath);
    }
}
