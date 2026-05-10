package io.github.spike.myai.shared.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 统一的 REST 异常处理器。
 *
 * <p>将控制器层显式抛出的 {@link ResponseStatusException} 直接转换为 JSON 错误响应，
 * 避免请求再次进入 Spring Boot 默认的 {@code /error} 分发链路后被 Security
 * 入口点改写成通用的 401 / 403 响应。
 */
@RestControllerAdvice
public class GlobalRestExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String code = status == null ? "ERROR" : status.name();
        String message = ex.getReason();

        if (message == null || message.isBlank()) {
            message = status == null ? "request failed" : status.getReasonPhrase();
        }

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(code, message));
    }
}
