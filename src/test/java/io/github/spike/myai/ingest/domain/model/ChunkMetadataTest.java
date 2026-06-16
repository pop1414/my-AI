package io.github.spike.myai.ingest.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChunkMetadataTest {

    // ===== 直接构造 =====

    @Test
    @DisplayName("直接构造：headings 应做防御性拷贝——修改原 list 不影响 ChunkMetadata")
    void constructor_shouldDefensivelyCopyHeadings_whenMutableListPassed() {
        ArrayList<String> original = new ArrayList<>(List.of("第一章", "1.1 节"));
        ChunkMetadata metadata = new ChunkMetadata(original, 1, ChunkContentType.PARAGRAPH);

        original.add("篡改");

        assertEquals(List.of("第一章", "1.1 节"), metadata.headings());
    }

    @Test
    @DisplayName("直接构造：headings 为 null 时应抛出 NullPointerException")
    void constructor_shouldThrowNPE_whenHeadingsNull() {
        assertThrows(NullPointerException.class,
                () -> new ChunkMetadata(null, 0, ChunkContentType.PARAGRAPH));
    }

    @Test
    @DisplayName("直接构造：contentType 为 null 时应抛出 NullPointerException")
    void constructor_shouldThrowNPE_whenContentTypeNull() {
        assertThrows(NullPointerException.class,
                () -> new ChunkMetadata(List.of(), 0, null));
    }

    @Test
    @DisplayName("直接构造：空 headings list 合法——TXT 等无标题格式场景")
    void constructor_shouldAcceptEmptyHeadings_whenEmptyListPassed() {
        ChunkMetadata metadata = new ChunkMetadata(List.of(), 0, ChunkContentType.PARAGRAPH);

        assertTrue(metadata.headings().isEmpty());
    }

    @Test
    @DisplayName("直接构造：pageNumber 为负数时应抛出 IllegalArgumentException")
    void constructor_shouldThrow_whenNegativePageNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkMetadata(List.of(), -1, ChunkContentType.PARAGRAPH));
    }

    @Test
    @DisplayName("直接构造：返回的 headings list 应不可变")
    void headings_shouldBeImmutable_whenReturned() {
        ChunkMetadata metadata = new ChunkMetadata(List.of("标题"), 1, ChunkContentType.HEADING);

        assertThrows(UnsupportedOperationException.class,
                () -> metadata.headings().add("新增标题"));
    }

    // ===== factory of() =====

    @Nested
    @DisplayName("of() 工厂方法")
    class OfFactoryMethod {

        @Test
        @DisplayName("headings 为 null 时应归一化为空 list")
        void of_shouldNormalizeNullHeadings_whenNullPassed() {
            ChunkMetadata metadata = ChunkMetadata.of(null, 0, ChunkContentType.PARAGRAPH);

            assertTrue(metadata.headings().isEmpty());
        }

        @Test
        @DisplayName("contentType 为 null 时应默认为 PARAGRAPH")
        void of_shouldDefaultContentType_whenNullPassed() {
            ChunkMetadata metadata = ChunkMetadata.of(null, 0, null);

            assertEquals(ChunkContentType.PARAGRAPH, metadata.contentType());
        }

        @Test
        @DisplayName("headings list 中的 null 元素应被过滤")
        void of_shouldFilterNullElementsFromHeadings() {
            ArrayList<String> input = new ArrayList<>();
            input.add("第一章");
            input.add(null);
            input.add("1.2 节");

            ChunkMetadata metadata = ChunkMetadata.of(input, 1, ChunkContentType.PARAGRAPH);

            assertEquals(List.of("第一章", "1.2 节"), metadata.headings());
        }

        @Test
        @DisplayName("headings list 中的空白字符串应被过滤")
        void of_shouldFilterBlankStringsFromHeadings() {
            List<String> input = List.of("第一章", "", "  ", "1.2 节");

            ChunkMetadata metadata = ChunkMetadata.of(input, 1, ChunkContentType.PARAGRAPH);

            assertEquals(List.of("第一章", "1.2 节"), metadata.headings());
        }

        @Test
        @DisplayName("pageNumber 为负数时应抛出 IllegalArgumentException")
        void of_shouldThrow_whenNegativePageNumber() {
            assertThrows(IllegalArgumentException.class,
                    () -> ChunkMetadata.of(null, -1, ChunkContentType.PARAGRAPH));
        }

        @Test
        @DisplayName("headings 产出应不可变")
        void of_shouldReturnImmutableHeadings() {
            ChunkMetadata metadata = ChunkMetadata.of(List.of("标题"), 1, ChunkContentType.HEADING);

            assertThrows(UnsupportedOperationException.class,
                    () -> metadata.headings().add("新增标题"));
        }
    }
}
