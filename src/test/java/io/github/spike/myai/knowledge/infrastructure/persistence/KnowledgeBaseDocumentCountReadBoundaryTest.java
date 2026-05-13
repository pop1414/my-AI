package io.github.spike.myai.knowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KnowledgeBaseDocumentCountReadBoundaryTest {

    @Test
    @DisplayName("知识库文档计数不得从主表旧 status 字段读取版本事实")
    void knowledgeBaseRepository_shouldCountByLatestProjection() throws IOException {
        Path sourcePath = Path.of("src/main/java/io/github/spike/myai/knowledge/infrastructure/persistence/JdbcKnowledgeBaseRepository.java");
        String source = Files.readString(sourcePath);

        assertTrue(source.contains("doc.latest_status = 'INDEXED'"));
        assertFalse(source.contains("doc.status = 'INDEXED'"));
    }
}
