package io.github.spike.myai.qa.application.usecase;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskQuestionResult;

/**
 * 文档问答用例接口（Application Use Case）。
 *
 * <p>接口层通过该契约触发问答流程，避免直接依赖具体服务实现。
 * 这有助于在测试中替换实现，也便于后续演进为异步/流式问答形态。
 */
public interface AskQuestionUseCase {

    /**
     * 执行一次同步问答。
     *
     * @param command 问答命令，包含问题、知识库范围与引用条数约束
     * @return 问答结果（回答文本 + 引用列表）
     */
    AskQuestionResult handle(AskQuestionCommand command);
}
