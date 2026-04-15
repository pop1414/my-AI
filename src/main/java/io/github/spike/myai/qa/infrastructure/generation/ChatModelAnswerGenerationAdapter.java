package io.github.spike.myai.qa.infrastructure.generation;

import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI {@link ChatModel} 的回答生成适配器。
 *
 * <p>该类位于基础设施层，负责把领域端口
 * {@link io.github.spike.myai.qa.domain.port.AnswerGenerationPort}
 * 适配到具体模型调用实现。
 */
@Component
public class ChatModelAnswerGenerationAdapter implements AnswerGenerationPort {

    private final ChatModel chatModel;

    public ChatModelAnswerGenerationAdapter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generateAnswer(String prompt) {
        // 直接委托给模型组件，提示词组装由应用层负责。
        return chatModel.call(prompt);
    }
}
