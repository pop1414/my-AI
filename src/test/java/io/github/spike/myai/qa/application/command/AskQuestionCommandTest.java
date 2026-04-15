package io.github.spike.myai.qa.application.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AskQuestionCommand 单元测试。
 */
class AskQuestionCommandTest {

    @Test
    @DisplayName("未传 kbId/topK 时应回退默认值")
    void command_shouldResolveDefaultValues() {
        AskQuestionCommand command = new AskQuestionCommand(" 你好 ", null, null);

        assertEquals("你好", command.normalizedQuestion());
        assertEquals("default", command.resolvedKbId());
        assertEquals(5, command.resolvedTopK());
    }

    @Test
    @DisplayName("question 为空时应抛出非法参数异常")
    void command_shouldThrow_whenQuestionIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new AskQuestionCommand(" ", "kb-1", 5));
    }

    @Test
    @DisplayName("topK 越界时应抛出非法参数异常")
    void command_shouldThrow_whenTopKOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new AskQuestionCommand("q", "kb-1", 0));
        assertThrows(IllegalArgumentException.class, () -> new AskQuestionCommand("q", "kb-1", 21));
    }
}
