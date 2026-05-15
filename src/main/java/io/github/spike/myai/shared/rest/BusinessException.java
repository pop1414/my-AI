package io.github.spike.myai.shared.rest;

import org.springframework.http.HttpStatus;

/**
 * 可携带机器可判定业务错误码的运行时异常。
 *
 * <p>与传统仅携带 HTTP 状态码的异常不同，{@code BusinessException}
 * 额外携带一个稳定的业务错误码（{@code code}），使前端可以按场景展示
 * 精确的差异化提示，而不必依赖自然语言消息做字符串匹配。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>版本冲突（如 VERSION_CONFLICT_STALE_LATEST_VERSION）；</li>
 *   <li>权限不足（如 VERSION_UPLOAD_NO_MANAGE_PERMISSION）；</li>
 *   <li>状态不允许（如 VERSION_UPLOAD_NOT_ALLOWED_STATUS）。</li>
 * </ul>
 *
 * <p>该异常由 {@link GlobalRestExceptionHandler#handleBusinessException(BusinessException)}
 * 统一捕获并转换为结构化 JSON 错误响应。
 *
 * @author Spike
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {

    /** HTTP 状态码，决定响应的 HTTP status line */
    private final HttpStatus status;
    /** 业务错误码，前端据此做差异化展示 */
    private final String code;

    /**
     * 构造一个携带稳定业务错误码的异常。
     *
     * <p>构造时即校验 status 和 code 的合法性，确保异常对象始终处于有效状态。
     *
     * @param status  HTTP 状态码，不能为 null
     * @param code    业务错误码，不能为空或空白
     * @param message 人类可读的错误描述
     * @throws IllegalArgumentException 当 status 为 null 或 code 为空/空白时
     */
    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        // 防御性校验：status 不能为 null
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        // 防御性校验：code 不能为空或空白，确保前端始终能拿到有效错误码
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.status = status;
        this.code = code;
    }

    /**
     * 返回 HTTP 状态码。
     *
     * @return HTTP 状态码，保证非 null
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 返回业务错误码。
     *
     * @return 业务错误码，保证非空且非空白
     */
    public String code() {
        return code;
    }
}
