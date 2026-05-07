package io.github.spike.myai.knowledge.application.usecase;

import io.github.spike.myai.knowledge.application.command.UpdateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;

/**
 * 编辑知识库用例。
 */
public interface UpdateKnowledgeBaseUseCase {

    KnowledgeBaseResult handle(UpdateKnowledgeBaseCommand command);
}
