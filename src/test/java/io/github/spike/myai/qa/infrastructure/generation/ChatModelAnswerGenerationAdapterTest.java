package io.github.spike.myai.qa.infrastructure.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;

/**
 * ChatModelAnswerGenerationAdapter 单元测试。
 */
class ChatModelAnswerGenerationAdapterTest {

    @Test
    @DisplayName("generateAnswer 应委托 ChatModel.call")
    void generateAnswer_shouldDelegateToChatModel() {
        ChatModel chatModel = Mockito.mock(ChatModel.class);
        ChatModelAnswerGenerationAdapter adapter = new ChatModelAnswerGenerationAdapter(chatModel);
        when(chatModel.call("prompt")).thenReturn("answer");

        String result = adapter.generateAnswer("prompt");

        verify(chatModel).call("prompt");
        assertEquals("answer", result);
    }
}
