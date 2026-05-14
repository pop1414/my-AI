package io.github.spike.myai.ingest.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceHintTest {

    @Test
    @DisplayName("标题为空时不生成 sourceHint")
    void heading_shouldReturnEmptyForBlankHeading() {
        assertTrue(SourceHint.heading(null).isEmpty());
        assertTrue(SourceHint.heading(" ").isEmpty());
        assertNull(SourceHint.heading(" ").toStorageValue());
    }

    @Test
    @DisplayName("标题中的 JSON 特殊字符应被转义")
    void toStorageValue_shouldEscapeJsonSpecialCharacters() {
        SourceHint sourceHint = SourceHint.heading("第1章 \"结构\" \\ path");

        assertEquals("{\"heading\":\"第1章 \\\"结构\\\" \\\\ path\"}", sourceHint.toStorageValue());
    }

    @Test
    @DisplayName("存储值读回后应保留等价 sourceHint 输出")
    void fromStorageValue_shouldRoundTripHeadingJson() {
        SourceHint sourceHint = SourceHint.fromStorageValue("{\"heading\":\"第1章 \\\"结构\\\" \\\\ path\"}");

        assertEquals("第1章 \"结构\" \\ path", sourceHint.heading());
        assertEquals("{\"heading\":\"第1章 \\\"结构\\\" \\\\ path\"}", sourceHint.toStorageValue());
    }

    @Test
    @DisplayName("未知历史 sourceHint 应原样保留")
    void fromStorageValue_shouldPreserveUnknownLegacyValue() {
        SourceHint sourceHint = SourceHint.fromStorageValue("{\"page\":3}");

        assertEquals("{\"page\":3}", sourceHint.toStorageValue());
    }
}
