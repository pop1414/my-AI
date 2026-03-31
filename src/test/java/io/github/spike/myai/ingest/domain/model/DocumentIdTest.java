package io.github.spike.myai.ingest.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DocumentId 值对象测试。
 *
 * <p>测试目标：验证领域不变量（不能为空）是否生效。
 */
class DocumentIdTest {

    @Test
    @DisplayName("传入非空字符串时，应成功创建 DocumentId")
    void shouldCreateDocumentId_whenValueIsValid() {
        DocumentId documentId = new DocumentId("doc-ok");
        assertEquals("doc-ok", documentId.value());
    }

    @Test
    @DisplayName("传入空白字符串时，应抛出 IllegalArgumentException")
    void shouldThrowException_whenValueIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(" "));
    }

    @Test
    @DisplayName("传入 null 时，应抛出 IllegalArgumentException")
    void shouldThrowException_whenValueIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(null));
    }
}
