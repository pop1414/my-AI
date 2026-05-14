package io.github.spike.myai.ingest.infrastructure.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 按 BOM 或严格 UTF-8 解码原生文本文件。
 */
final class NativeTextDecoder {

    DecodedText decode(byte[] content) throws CharacterCodingException {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF) {
            return new DecodedText(decodeStrict(StandardCharsets.UTF_8, content, 3), StandardCharsets.UTF_8);
        }
        if (content.length >= 2
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xFE) {
            return new DecodedText(decodeStrict(StandardCharsets.UTF_16LE, content, 2), StandardCharsets.UTF_16LE);
        }
        if (content.length >= 2
                && (content[0] & 0xFF) == 0xFE
                && (content[1] & 0xFF) == 0xFF) {
            return new DecodedText(decodeStrict(StandardCharsets.UTF_16BE, content, 2), StandardCharsets.UTF_16BE);
        }
        return new DecodedText(decodeStrict(StandardCharsets.UTF_8, content, 0), StandardCharsets.UTF_8);
    }

    private static String decodeStrict(Charset charset, byte[] content, int offset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(content, offset, content.length - offset)).toString();
    }

    record DecodedText(String text, Charset charset) {}
}
