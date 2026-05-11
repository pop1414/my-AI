package io.github.spike.myai.shared.rest;

import io.github.spike.myai.auth.application.exception.GovernanceAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一的 REST 异常处理器。
 *
 * <p>使用 {@link RestControllerAdvice} 拦截所有控制器抛出的异常，
 * 将其转换为结构化的 JSON 错误响应（{@link ErrorResponse}），
 * 避免请求进入 Spring Boot 默认的 {@code /error} 分发链路后
 * 被 Security 入口点改写为通用的 401 / 403 响应。
 *
 * <h3>处理的异常类型</h3>
 * <ul>
 *   <li>{@link ResponseStatusException} — 控制器显式抛出的 HTTP 状态异常，
 *       直接映射为对应的 HTTP 状态码和消息体；</li>
 *   <li>{@link AccessDeniedException} — Spring Security 或治理守卫抛出的
 *       权限拒绝异常：
 *     <ul>
 *       <li>普通 AccessDeniedException → code 固定为 {@code FORBIDDEN}；</li>
 *       <li>{@link GovernanceAccessDeniedException} → code 取自异常的
 *           {@code reasonCode} 字段，便于前端差异化处理。</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    /**
     * 处理控制器显式抛出的 {@link ResponseStatusException}。
     *
     * <p>从异常中提取 HTTP 状态码和原因消息，组装为 {@link ErrorResponse} 返回。
     * 若原因消息为空，则回退为状态码的标准短语。
     *
     * @param ex 响应状态异常
     * @return 包含错误码和消息的 JSON 响应体
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        // 解析 HTTP 状态码，状态码非法时回退为 "ERROR"
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status == null ? "ERROR" : status.name();
        String message = ex.getReason();

        // 原因消息为空时，回退为状态码的标准英文短语
        if (message == null || message.isBlank()) {
            message = status == null ? "request failed" : status.getReasonPhrase();
        }

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(code, message));
    }

    /**
     * 处理权限拒绝异常（Spring Security 或治理守卫）。
     *
     * <p>统一映射为 HTTP 403 Forbidden，但根据异常子类型设置差异化的错误码：
     * <ul>
     *   <li>普通 {@link AccessDeniedException} → code 固定为 "FORBIDDEN"；</li>
     *   <li>{@link GovernanceAccessDeniedException} → code 取自
     *       {@code reasonCode} 字段（如 owner_immutable），
     *       使前端可据此展示差异化的提示语。</li>
     * </ul>
     *
     * @param ex 权限拒绝异常
     * @return HTTP 403 响应，Body 含自定义错误码
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        // 默认错误码为 FORBIDDEN
        String code = "FORBIDDEN";
        // 治理守卫抛出的异常携带细粒度 reasonCode，用于前端差异化展示
        if (ex instanceof GovernanceAccessDeniedException governanceEx) {
            code = governanceEx.reasonCode();
        }

        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = "access is denied";
        }

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(code, message));
    }
}
