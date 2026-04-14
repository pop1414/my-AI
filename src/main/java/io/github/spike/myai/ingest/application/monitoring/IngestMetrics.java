package io.github.spike.myai.ingest.application.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * ingest 关键链路指标聚合。
 *
 * <p>命名统一使用 myai.ingest.*，便于后续接入监控看板。
 */
@Component
public class IngestMetrics {

    private final Counter processSuccessCounter; // 成功处理的文档计数器
    private final Counter processFailedCounter; // 处理失败（标记为 FAILED）的文档计数器
    private final Counter processRetryScheduledCounter; // 因暂时性失败而调度重试的文档计数器
    private final Counter deleteConflictCounter; // 因状态冲突而被拒绝的删除请求计数器
    private final Counter deleteSuccessCounter; // 成功完成的删除请求计数器

    public IngestMetrics(MeterRegistry meterRegistry) {
        this.processSuccessCounter = Counter.builder("myai.ingest.process.success.total")
                .description("Number of documents processed successfully")
                .register(meterRegistry);
        this.processFailedCounter = Counter.builder("myai.ingest.process.failed.total")
                .description("Number of documents marked FAILED")
                .register(meterRegistry);
        this.processRetryScheduledCounter = Counter.builder("myai.ingest.process.retry_scheduled.total")
                .description("Number of transient failures scheduled for retry")
                .register(meterRegistry);
        this.deleteConflictCounter = Counter.builder("myai.ingest.delete.conflict.total")
                .description("Number of delete requests rejected by status conflict")
                .register(meterRegistry);
        this.deleteSuccessCounter = Counter.builder("myai.ingest.delete.success.total")
                .description("Number of delete requests completed successfully")
                .register(meterRegistry);
    }

    /**
     * 增加处理成功计数。
     */
    public void incrementProcessSuccess() {
        processSuccessCounter.increment();
    }

    /**
     * 增加处理失败计数。
     */
    public void incrementProcessFailed() {
        processFailedCounter.increment();
    }

    /**
     * 增加重试调度计数。
     */
    public void incrementRetryScheduled() {
        processRetryScheduledCounter.increment();
    }

    /**
     * 增加删除冲突计数。
     */
    public void incrementDeleteConflict() {
        deleteConflictCounter.increment();
    }

    /**
     * 增加删除成功计数。
     */
    public void incrementDeleteSuccess() {
        deleteSuccessCounter.increment();
    }
}
