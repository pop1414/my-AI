package io.github.spike.myai.auth.application.exception;

/**
 * 知识库授权治理场景下的知识库不存在异常。
 * <p>
 * 与通用的 {@link io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException} 区分，
 * 本异常专用于授权治理上下文中知识库存在性校验失败的场景，
 * 语义更明确，便于上层控制器精确映射 HTTP 状态码。
 * <p>
 * 继承自 {@link RuntimeException}，由控制器转换为 HTTP 404 响应。
 *
 * @author spike
 * @since 1.0.0
 */
public class ManagedKnowledgeBaseNotFoundException extends RuntimeException {

    /**
     * 构造异常实例。
     *
     * @param message 异常描述信息，应包含知识库 ID 以便排查
     */
    public ManagedKnowledgeBaseNotFoundException(String message) {
        super(message);
    }
}
