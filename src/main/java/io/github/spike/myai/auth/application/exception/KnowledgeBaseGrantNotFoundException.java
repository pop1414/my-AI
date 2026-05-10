package io.github.spike.myai.auth.application.exception;

/**
 * 知识库授权不存在异常。
 * <p>
 * 当查询或操作知识库授权记录时，未能匹配到目标授权关系时抛出。
 * 继承自 {@link RuntimeException}，属于非受检异常，
 * 由上层控制器转换为 HTTP 404 响应。
 * <p>
 * 典型触发场景：
 * <ul>
 *   <li>指定知识库下不存在对该用户的授权记录</li>
 *   <li>授权记录状态已非 {@code ACTIVE}（如已被回收或禁用）</li>
 *   <li>并发场景下授权记录已被删除</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public class KnowledgeBaseGrantNotFoundException extends RuntimeException {

    /**
     * 构造异常实例。
     *
     * @param message 异常描述信息，应包含知识库 ID 和用户 ID 等上下文线索
     */
    public KnowledgeBaseGrantNotFoundException(String message) {
        super(message);
    }
}
