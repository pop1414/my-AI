package io.github.spike.myai.knowledge.application.usecase;

import io.github.spike.myai.knowledge.application.command.CreateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;

/**
 * 创建知识库用例。
 */
public interface CreateKnowledgeBaseUseCase {

    KnowledgeBaseResult handle(CreateKnowledgeBaseCommand command);
}
