package io.github.spike.myai.ingest.application.exception;

/**
 * 文档删除冲突异常。
 *
 * <p>用于表达“当前文档状态不允许删除”（例如 INGESTING / DELETING）。
 */
public class DocumentDeleteConflictException extends RuntimeException {

    public DocumentDeleteConflictException(String message) {
        super(message);
    }
}
