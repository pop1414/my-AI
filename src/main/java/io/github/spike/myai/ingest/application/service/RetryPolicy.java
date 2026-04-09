package io.github.spike.myai.ingest.application.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 重试策略：判断异常是否为瞬时错误（is_transient）。
 *
 * <p>设计目标：
 * <ul>
 *   <li>最大化“可恢复”错误的自动处理能力。</li>
 *   <li>避免对永久性错误的无意义重试。</li>
 *   <li>兼容 web / webflux 两类调用栈。</li>
 * </ul>
 */
@Component
public class RetryPolicy {

    /**
     * 评估给定的异常，决定是否进行重试。
     * 该方法会遍历异常的因果链（Cause Chain），最多向下查找10层，
     * 一旦在任意一层找到已知可分类的异常细类，立即返回其对应的重试决策。
     * 如果未匹配任何已知规则，则默认当做不可重试异常（非瞬时错误）。
     *
     * @param exception 捕获到的异常对象
     * @return 包含重试建议（是否为瞬时错误）和错误特征的代码决策对象
     */
    public RetryDecision decide(Throwable exception) {
        // 入参保护：避免空异常导致 NPE。
        if (exception == null) {
            return new RetryDecision(false, "NULL_EXCEPTION", "Exception is null");
        }
        // 递归向上寻找根因，避免只看顶层包装异常导致误判。
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            RetryDecision decision = classify(cause);
            if (decision != null) {
                return decision;
            }
        }
        return new RetryDecision(false, classifyCode(exception), safeMessage(exception));
    }

    /**
     * 根据具体的异常类型，进行细致的分类并生成重试决策。
     *
     * @param cause 当前正在检查的异常节点
     * @return 如果可以明确分类，则返回对应的决策对象；如无法识别，返回 null 以使上层继续查探下个 root cause。
     */
    private RetryDecision classify(Throwable cause) {
        // 1) Spring MVC / RestTemplate 场景：根据 HTTP 状态判断是否重试。
        if (cause instanceof HttpStatusCodeException ex) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            return fromHttpStatus(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, safeMessage(ex));
        }
        // 2) WebClient（webflux）场景：使用反射兜底处理（不强依赖 webflux 依赖）。
        RetryDecision webClientDecision = classifyWebClientException(cause);
        if (webClientDecision != null) {
            return webClientDecision;
        }
        // 3) 数据库/连接池等瞬时错误。
        if (cause instanceof TransientDataAccessException
                || cause instanceof SQLTransientException
                || cause instanceof SQLRecoverableException) {
            return new RetryDecision(true, classifyCode(cause), safeMessage(cause));
        }
        // 4) 网络抖动与超时。
        if (cause instanceof SocketTimeoutException
                || cause instanceof ConnectException
                || cause instanceof UnknownHostException) {
            return new RetryDecision(true, classifyCode(cause), safeMessage(cause));
        }
        // 5) IO 异常：默认视为瞬时错误，但需排除“磁盘满”等永久性情况。
        if (cause instanceof IOException io) {
            if (isDiskFull(io)) {
                return new RetryDecision(false, classifyCode(io), safeMessage(io));
            }
            return new RetryDecision(true, classifyCode(io), safeMessage(io));
        }
        // 6) 参数/校验类错误：直接失败，不重试。
        if (cause instanceof IllegalArgumentException) {
            return new RetryDecision(false, classifyCode(cause), safeMessage(cause));
        }
        return null;
    }

    /**
     * 专门用于处理 WebClient 抛出的异常 (如 WebClientResponseException)。
     * 这里通过全限定类名比对和反射技术提取信息，目的是为了在没有引入 spring-webflux
     * 依赖时（如仅为纯 Spring MVC 项目），代码也能安全运行而不会抛出 ClassNotFoundException。
     *
     * @param cause 需要检查的异常对象
     * @return 如果是 WebClient 异常且包含状态码，则返回匹配的重试策略；否则返回 null
     */
    private RetryDecision classifyWebClientException(Throwable cause) {
        if (!"org.springframework.web.reactive.function.client.WebClientResponseException"
                .equals(cause.getClass().getName())) {
            return null;
        }
        Integer statusCode = tryGetStatusCode(cause);
        if (statusCode == null) {
            return null;
        }
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            return null;
        }
        return fromHttpStatus(status, safeMessage(cause));
    }

    /**
     * 利用反射尝试从异常对象中提取 HTTP 状态码数字。
     * 主要是兼容 Spring WebClient 中 WebClientResponseException 各版本
     * (获取状态码可能是 getRawStatusCode 或 getStatusCode().value()) 的区别。
     *
     * @param cause 试图提取状态码的异常
     * @return 提取成功则返回 HTTP 状态码，失败返回 null
     */
    private Integer tryGetStatusCode(Throwable cause) {
        // WebClientResponseException.getRawStatusCode()
        try {
            Object raw = cause.getClass().getMethod("getRawStatusCode").invoke(cause);
            if (raw instanceof Integer value) {
                return value;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        // WebClientResponseException.getStatusCode().value()
        try {
            Object statusCode = cause.getClass().getMethod("getStatusCode").invoke(cause);
            if (statusCode != null) {
                Object value = statusCode.getClass().getMethod("value").invoke(statusCode);
                if (value instanceof Integer intValue) {
                    return intValue;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /**
     * 根据 HTTP 状态码决定是否标记为“瞬时错误”（意味着可以重试）。
     * 策略规则：408(请求超时)、429(过多请求) 和所有 5xx(服务器侧异常) 被认为是瞬时错误。
     * 其他如 400(损坏的请求)、401(未鉴权)、403(禁止) 或 404(未找到) 皆视为应立即中止的客户端永久错误。
     *
     * @param status HTTP 状态对象
     * @param message 附带返回的异常文案
     * @return 填充了状态的重试决策结果
     */
    private RetryDecision fromHttpStatus(HttpStatus status, String message) {
        int code = status.value();
        // 408/429/5xx => 瞬时错误；其他视为非瞬时。
        boolean isTransient = code == 408 || code == 429 || code >= 500;
        return new RetryDecision(isTransient, "HTTP_" + code, message);
    }

    /**
     * 判定引发的 IOException 是否归因于所在节点磁盘空间已满。
     * 磁盘满的故障通常难以通过简单的瞬时等待与重试进行自我恢复，被视作永久性受阻（应触发报警人工介入）。
     *
     * @param io 捕获的 IO 异常
     * @return 错误信息中若带有常见“磁盘满”文案，返回 true
     */
    private static boolean isDiskFull(IOException io) {
        String message = io.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("No space left on device") || message.contains("disk full");
    }

    /**
     * 将异常类名抽取为简短标识代码，以便记录结构化日志和标识错误归属。
     *
     * @param exception 目标异常
     * @return 异常类的简单类名
     */
    private static String classifyCode(Throwable exception) {
        return exception.getClass().getSimpleName();
    }

    /**
     * 提取异常对象中的消息文字信息。如果提示为空，则回退使用异常自身类的简名，
     * 以防在处理某些没有 Message 的业务或底层异常时丢失上下文字段。
     *
     * @param exception 目标异常
     * @return 可安全消费且非空的字符串摘要
     */
    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }

    /**
     * 表示重试评估决策的返回实体对象。
     *
     * @param transientError 标识当前错误能否在未来短暂重试后自愈复原（true表示可以重试）
     * @param errorCode 从异常类别或 HTTP 响应中提取到的一级错误标识
     * @param errorMessage 分析出的具体错误原因提示文字
     */
    public record RetryDecision(boolean transientError, String errorCode, String errorMessage) {
    }
}
