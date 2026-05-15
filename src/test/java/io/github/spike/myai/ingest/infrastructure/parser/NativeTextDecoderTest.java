package io.github.spike.myai.ingest.infrastructure.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NativeTextDecoderTest {

    private final NativeTextDecoder decoder = new NativeTextDecoder();

    @Test
    @DisplayName("无 BOM 文本应按严格 UTF-8 解码")
    void decode_shouldUseStrictUtf8WithoutBom() throws Exception {
        NativeTextDecoder.DecodedText decoded = decoder.decode("正文".getBytes(StandardCharsets.UTF_8));

        assertEquals("正文", decoded.text());
        assertEquals(StandardCharsets.UTF_8, decoded.charset());
    }

    @Test
    @DisplayName("UTF-8 BOM 应被跳过")
    void decode_shouldSkipUtf8Bom() throws Exception {
        byte[] text = "标题".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[text.length + 3];
        content[0] = (byte) 0xEF;
        content[1] = (byte) 0xBB;
        content[2] = (byte) 0xBF;
        System.arraycopy(text, 0, content, 3, text.length);

        NativeTextDecoder.DecodedText decoded = decoder.decode(content);

        assertEquals("标题", decoded.text());
        assertEquals(StandardCharsets.UTF_8, decoded.charset());
    }

    @Test
    @DisplayName("UTF-16LE BOM 应按 UTF-16LE 解码")
    void decode_shouldUseUtf16LeWhenBomExists() throws Exception {
        byte[] text = "标题".getBytes(StandardCharsets.UTF_16LE);
        byte[] content = new byte[text.length + 2];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xFE;
        System.arraycopy(text, 0, content, 2, text.length);

        NativeTextDecoder.DecodedText decoded = decoder.decode(content);

        assertEquals("标题", decoded.text());
        assertEquals(StandardCharsets.UTF_16LE, decoded.charset());
    }

    @Test
    @DisplayName("非法 UTF-8 字节应抛出异常以便回退 Tika")
    void decode_shouldRejectMalformedUtf8() {
        assertThrows(CharacterCodingException.class, () -> decoder.decode(new byte[] {(byte) 0xC3, 0x28}));
    }
}
