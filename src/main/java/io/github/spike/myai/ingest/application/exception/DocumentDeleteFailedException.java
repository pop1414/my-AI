package io.github.spike.myai.ingest.application.exception;

/**
 * 文档删除执行失败异常。
 *
 * <p>用于表达删除流程中资源清理失败（源文件或向量删除失败）。
 */
public class DocumentDeleteFailedException extends RuntimeException {

    public DocumentDeleteFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
