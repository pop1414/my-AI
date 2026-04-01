package io.github.spike.myai.ingest.application.exception;

/**
 * 文档不存在异常（Application Exception）。
 *
 * <p>该异常表示请求的文档 ID 在仓储中未命中。
 * 在接口层会被转换为 404 Not Found。
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String message) {
        super(message);
    }
}
