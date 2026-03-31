package io.github.spike.myai.ingest.infrastructure.id;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UuidDocumentIdGenerator 基础设施适配器测试。
 *
 * <p>测试目标：
 * <ul>
 *     <li>每次调用都返回非空 DocumentId。</li>
 *     <li>连续两次生成的 ID 不相同。</li>
 * </ul>
 */
class UuidDocumentIdGeneratorTest {

    @Test
    @DisplayName("应生成非空且彼此不同的文档 ID")
    void nextId_shouldGenerateUniqueDocumentId() {
        UuidDocumentIdGenerator generator = new UuidDocumentIdGenerator();

        DocumentId id1 = generator.nextId();
        DocumentId id2 = generator.nextId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotNull(id1.value());
        assertNotNull(id2.value());
        assertNotEquals(id1.value(), id2.value());
    }
}
