package io.github.spike.myai.knowledge.application.exception;

/**
 * 知识库不存在异常。
 */
public class KnowledgeBaseNotFoundException extends RuntimeException {

    public KnowledgeBaseNotFoundException(String message) {
        super(message);
    }
}
