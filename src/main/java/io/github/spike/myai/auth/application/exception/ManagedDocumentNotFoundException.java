package io.github.spike.myai.auth.application.exception;

/**
 * 文档授权治理场景下的文档不存在异常。
 * <p>
 * 与文档领域可能存在的通用文档不存在异常区分，
 * 本异常专用于授权治理上下文中文档存在性校验失败的场景，
 * 语义更明确，便于上层控制器精确映射 HTTP 404 状态码。
 *
 * @author spike
 * @since 1.0.0
 */
public class ManagedDocumentNotFoundException extends RuntimeException {

    /**
     * 构造异常实例。
     *
     * @param message 异常描述信息，应包含文档 ID 以便排查
     */
    public ManagedDocumentNotFoundException(String message) {
        super(message);
    }
}
