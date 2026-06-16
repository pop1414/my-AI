package io.github.spike.myai.ingest.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.health.HealthCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * DoclingStartupVerifier 的纯单元测试。
 *
 * @author spike
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DoclingStartupVerifierTest {

    @Mock
    private DoclingServeApi doclingServeApi;

    @Test
    @DisplayName("启动校验 — Docling 健康时正常完成，不抛异常")
    void start_shouldSucceed_whenDoclingIsHealthy() {
        // given
        HealthCheckResponse healthyResponse = HealthCheckResponse.builder().status("ok").build();
        when(doclingServeApi.health()).thenReturn(healthyResponse);
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi);

        // when
        verifier.start();

        // then
        assertTrue(verifier.isRunning());
    }

    @Test
    @DisplayName("启动校验 — Docling 返回非 ok 状态时抛出异常")
    void start_shouldThrowException_whenDoclingReturnsUnhealthyStatus() {
        // given
        HealthCheckResponse unhealthyResponse = HealthCheckResponse.builder().status("error").build();
        when(doclingServeApi.health()).thenReturn(unhealthyResponse);
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi);

        // when & then
        DoclingUnavailableException exception = assertThrows(
                DoclingUnavailableException.class, verifier::start);
        assertTrue(exception.getMessage().contains("非 ok 状态"));
        assertFalse(verifier.isRunning());
    }

    @Test
    @DisplayName("启动校验 — Docling 连接超时重试耗尽后抛出异常")
    void start_shouldThrowException_whenDoclingConnectionTimesOut() {
        // given — maxRetries=1 避免测试等待
        when(doclingServeApi.health()).thenThrow(new ResourceAccessException("连接超时"));
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi, 1, Duration.ZERO);

        // when & then
        DoclingUnavailableException exception = assertThrows(
                DoclingUnavailableException.class, verifier::start);
        assertTrue(exception.getMessage().contains("无法连接到 Docling Serve API"));
        assertFalse(verifier.isRunning());
    }

    @Test
    @DisplayName("启动校验 — Docling 返回 5xx 错误重试耗尽后抛出异常")
    void start_shouldThrowException_whenDoclingReturns5xx() {
        // given — maxRetries=1 避免测试等待
        when(doclingServeApi.health())
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi, 1, Duration.ZERO);

        // when & then
        DoclingUnavailableException exception = assertThrows(
                DoclingUnavailableException.class, verifier::start);
        assertTrue(exception.getMessage().contains("无法连接到 Docling Serve API"));
        assertFalse(verifier.isRunning());
    }

    @Test
    @DisplayName("启动校验 — 连接失败后重试成功，正常完成")
    void start_shouldSucceed_whenRetrySucceeds() {
        // given — 第一次失败，第二次成功
        HealthCheckResponse healthyResponse = HealthCheckResponse.builder().status("ok").build();
        when(doclingServeApi.health())
                .thenThrow(new ResourceAccessException("连接超时"))
                .thenReturn(healthyResponse);
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi, 2, Duration.ZERO);

        // when
        verifier.start();

        // then
        assertTrue(verifier.isRunning());
        verify(doclingServeApi, times(2)).health();
    }

    @Test
    @DisplayName("启动校验 — 重试耗尽后抛出异常，cause 保留最后一次失败原因")
    void start_shouldThrowException_whenRetriesExhausted() {
        // given — 3 次全部失败
        when(doclingServeApi.health()).thenThrow(new ResourceAccessException("连接超时"));
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi, 3, Duration.ZERO);

        // when & then
        DoclingUnavailableException exception = assertThrows(
                DoclingUnavailableException.class, verifier::start);
        assertTrue(exception.getMessage().contains("无法连接到 Docling Serve API"));
        assertTrue(exception.getCause() instanceof ResourceAccessException);
        verify(doclingServeApi, times(3)).health();
    }

    @Test
    @DisplayName("启动校验 — stop 后 isRunning 返回 false")
    void stop_shouldSetNotRunning_whenCalledAfterStart() {
        // given
        HealthCheckResponse healthyResponse = HealthCheckResponse.builder().status("ok").build();
        when(doclingServeApi.health()).thenReturn(healthyResponse);
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi);
        verifier.start();
        assertTrue(verifier.isRunning());

        // when
        verifier.stop();

        // then
        assertFalse(verifier.isRunning());
    }

    @Test
    @DisplayName("启动校验 — getPhase 返回大值确保在其他 Lifecycle Bean 之后执行")
    void getPhase_shouldReturnHighValue() {
        // given
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi);

        // when & then
        assertEquals(Integer.MAX_VALUE - 100, verifier.getPhase());
    }

    @Test
    @DisplayName("启动校验 — 初始状态 isRunning 为 false")
    void isRunning_shouldBeFalse_whenNotStarted() {
        // given
        DoclingStartupVerifier verifier = new DoclingStartupVerifier(doclingServeApi);

        // when & then
        assertFalse(verifier.isRunning());
    }
}
