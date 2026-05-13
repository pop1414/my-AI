package io.github.spike.myai.ingest.infrastructure.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceHintEncoderTest {

    private final SourceHintEncoder encoder = new SourceHintEncoder();

    @Test
    @DisplayName("标题为空时不生成 sourceHint")
    void encodeHeading_shouldReturnNullForBlankHeading() {
        assertNull(encoder.encodeHeading(null));
        assertNull(encoder.encodeHeading(" "));
    }

    @Test
    @DisplayName("标题中的 JSON 特殊字符应被转义")
    void encodeHeading_shouldEscapeJsonSpecialCharacters() {
        assertEquals(
                "{\"heading\":\"第1章 \\\"结构\\\" \\\\ path\"}",
                encoder.encodeHeading("第1章 \"结构\" \\ path"));
    }
}
