package io.github.spike.myai.knowledge.application.exception;

/**
 * 知识库已停用异常。
 */
public class KnowledgeBaseInactiveException extends RuntimeException {

    public KnowledgeBaseInactiveException(String message) {
        super(message);
    }
}
