package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.spike.myai.ingest.domain.model.DoclingPermanentException;
import io.github.spike.myai.ingest.domain.model.DoclingTransientException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RetryPolicy 单元测试。
 *
 * <p>验证各种异常类型的分类逻辑，确保 Docling 异常与现有异常层次正确共存。
 */
class RetryPolicyTest {

    private final RetryPolicy retryPolicy = new RetryPolicy();

    // === Docling 异常分类 ===

    @Test
    @DisplayName("DoclingPermanentException 应分类为非瞬时错误（不重试）")
    void decide_shouldReturnNonTransient_whenDoclingPermanentException() {
        var exception = new DoclingPermanentException(
                "docling returned client error: 400", 400,
                new RuntimeException("original"));

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertFalse(decision.transientError());
        assertEquals("DOCLING_PERMANENT_400", decision.errorCode());
    }

    @Test
    @DisplayName("DoclingPermanentException(422) 应分类为非瞬时错误，errorCode 含状态码")
    void decide_shouldReturnNonTransient_whenDoclingPermanentException422() {
        var exception = new DoclingPermanentException(
                "docling returned client error: 422", 422,
                new RuntimeException("unprocessable"));

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertFalse(decision.transientError());
        assertEquals("DOCLING_PERMANENT_422", decision.errorCode());
    }

    @Test
    @DisplayName("DoclingTransientException 应分类为瞬时错误（可重试）")
    void decide_shouldReturnTransient_whenDoclingTransientException() {
        var exception = new DoclingTransientException(
                "docling returned server error: 500",
                new RuntimeException("server error"));

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertTrue(decision.transientError());
        assertEquals("DOCLING_TRANSIENT", decision.errorCode());
    }

    @Test
    @DisplayName("DoclingTransientException 包装 SocketTimeoutException 时应分类为瞬时错误")
    void decide_shouldReturnTransient_whenDoclingTransientExceptionWithCause() {
        var exception = new DoclingTransientException(
                "docling connection failed",
                new SocketTimeoutException("connect timed out"));

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertTrue(decision.transientError());
        assertEquals("DOCLING_TRANSIENT", decision.errorCode());
    }

    // === 现有异常分类（回归验证） ===

    @Test
    @DisplayName("IllegalStateException 应分类为非瞬时错误（输入校验不重试）")
    void decide_shouldReturnNonTransient_whenIllegalStateException() {
        var exception = new IllegalStateException("empty source content");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertFalse(decision.transientError());
        assertEquals("IllegalStateException", decision.errorCode());
    }

    @Test
    @DisplayName("SocketTimeoutException 应分类为瞬时错误")
    void decide_shouldReturnTransient_whenSocketTimeoutException() {
        var exception = new SocketTimeoutException("read timed out");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertTrue(decision.transientError());
        assertEquals("SocketTimeoutException", decision.errorCode());
    }

    @Test
    @DisplayName("ConnectException 应分类为瞬时错误")
    void decide_shouldReturnTransient_whenConnectException() {
        var exception = new ConnectException("connection refused");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertTrue(decision.transientError());
        assertEquals("ConnectException", decision.errorCode());
    }

    @Test
    @DisplayName("IOException 应分类为瞬时错误（非磁盘满）")
    void decide_shouldReturnTransient_whenIOException() {
        var exception = new IOException("broken pipe");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertTrue(decision.transientError());
        assertEquals("IOException", decision.errorCode());
    }

    @Test
    @DisplayName("IOException（磁盘满）应分类为非瞬时错误")
    void decide_shouldReturnNonTransient_whenDiskFull() {
        var exception = new IOException("No space left on device");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertFalse(decision.transientError());
        assertEquals("IOException", decision.errorCode());
    }

    @Test
    @DisplayName("IllegalArgumentException 应分类为非瞬时错误")
    void decide_shouldReturnNonTransient_whenIllegalArgumentException() {
        var exception = new IllegalArgumentException("invalid parameter");

        RetryPolicy.RetryDecision decision = retryPolicy.decide(exception);

        assertFalse(decision.transientError());
        assertEquals("IllegalArgumentException", decision.errorCode());
    }

    @Test
    @DisplayName("null 异常应分类为非瞬时错误")
    void decide_shouldReturnNonTransient_whenNullException() {
        RetryPolicy.RetryDecision decision = retryPolicy.decide(null);

        assertFalse(decision.transientError());
        assertEquals("NULL_EXCEPTION", decision.errorCode());
    }

    @Test
    @DisplayName("包装的 DoclingTransientException 应通过 cause chain 正确分类")
    void decide_shouldClassifyWrappedDoclingException_throughCauseChain() {
        var doclingException = new DoclingTransientException(
                "docling connection failed",
                new SocketTimeoutException("timeout"));
        var wrappedException = new RuntimeException("wrapper", doclingException);

        RetryPolicy.RetryDecision decision = retryPolicy.decide(wrappedException);

        assertTrue(decision.transientError());
        assertEquals("DOCLING_TRANSIENT", decision.errorCode());
    }
}
