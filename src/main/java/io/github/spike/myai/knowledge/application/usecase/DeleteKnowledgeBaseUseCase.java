package io.github.spike.myai.knowledge.application.usecase;

/**
 * 删除知识库用例。
 *
 * <p>删除采用软删除语义：将知识库状态置为 {@code DELETED}，
 * 保留知识库主数据、文档、授权和审计追溯记录。
 */
public interface DeleteKnowledgeBaseUseCase {

    /**
     * 软删除指定知识库。
     *
     * @param kbId 知识库业务键
     */
    void handle(String kbId);
}
