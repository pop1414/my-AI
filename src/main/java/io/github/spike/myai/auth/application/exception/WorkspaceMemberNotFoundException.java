package io.github.spike.myai.auth.application.exception;

/**
 * 工作区成员不存在异常。
 * <p>
 * 当查询或更新操作未能匹配到目标工作区中的活跃成员时抛出。
 * 继承自 {@link RuntimeException}，属于非受检异常，
 * 由上层控制器通过全局异常处理器或 try-catch 转换为 HTTP 404 响应。
 * <p>
 * 典型触发场景：
 * <ul>
 *   <li>用户 ID 在指定工作区中无对应成员关系</li>
 *   <li>成员关系或用户状态已非 {@code ACTIVE}</li>
 *   <li>并发场景下成员记录已被删除</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
public class WorkspaceMemberNotFoundException extends RuntimeException {

    /**
     * 构造异常实例。
     *
     * @param message 异常描述信息，应包含上下文线索（如用户 ID 或工作区 ID）以便排查
     */
    public WorkspaceMemberNotFoundException(String message) {
        super(message);
    }
}
