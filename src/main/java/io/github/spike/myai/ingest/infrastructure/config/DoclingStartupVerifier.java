package io.github.spike.myai.ingest.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.health.HealthCheckResponse;

/**
 * Docling Serve 启动连通性校验器。
 *
 * <p>在 Spring 容器所有 Bean 初始化完成后、内嵌服务器启动前，
 * 调用 {@link DoclingServeApi#health()} 验证 Docling Serve 可达。
 * 若健康检查失败，抛出 {@link DoclingUnavailableException} 触发 fail-fast。
 *
 * <p>使用 {@link SmartLifecycle} 而非 {@code @PostConstruct} 或 {@code ApplicationRunner}：
 * <ul>
 *   <li>{@code @PostConstruct} 在单个 Bean 初始化时执行，无法保证在所有 ingest 组件之后</li>
 *   <li>{@code ApplicationRunner} 在应用完全启动后执行，此时内嵌服务器已就绪，fail-fast 太晚</li>
 *   <li>{@code SmartLifecycle} 在所有 Bean 初始化完成后、内嵌服务器启动前执行，是最合适的拦截点</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 * @see DoclingUnavailableException
 * @see DoclingUnavailableFailureAnalyzer
 */
public class DoclingStartupVerifier implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DoclingStartupVerifier.class);

    private static final int PHASE = Integer.MAX_VALUE - 100;

    private final DoclingServeApi doclingServeApi;

    private volatile boolean running = false;

    public DoclingStartupVerifier(DoclingServeApi doclingServeApi) {
        this.doclingServeApi = doclingServeApi;
    }

    @Override
    public void start() {
        log.info("正在校验 Docling Serve 连通性...");

        try {
            HealthCheckResponse response = doclingServeApi.health();

            if (!"ok".equals(response.getStatus())) {
                throw new DoclingUnavailableException(
                        "Docling Serve 健康检查返回非 ok 状态: %s".formatted(response.getStatus()));
            }

            log.info("Docling Serve 连通性校验通过");
            running = true;
        } catch (DoclingUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new DoclingUnavailableException(
                    "Docling Serve 不可用 — 无法连接到 Docling Serve API", e);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
