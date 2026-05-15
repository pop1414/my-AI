package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ingest 审计事件记录器。
 *
 * <p>Ingest 治理动作通常已经完成数据库状态变更、对象存储写入或向量清理等主业务副作用。
 * 审计写入失败时不得反向改变主业务结果，因此这里采用 best-effort 记录策略：
 * 保存失败只写入警告日志，调用方继续保持原有业务返回或业务异常。
 */
final class IngestAuditEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(IngestAuditEventRecorder.class);

    private IngestAuditEventRecorder() {
    }

    /**
     * 安全保存审计事件。
     *
     * @param auditEventRepository 审计事件仓储
     * @param event 审计事件
     */
    static void save(AuditEventRepository auditEventRepository, AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception ex) {
            log.warn(
                    "Failed to save ingest audit event. eventType={}, targetType={}, targetId={}, outcome={}",
                    event.eventType(),
                    event.targetType(),
                    event.targetId(),
                    event.outcome(),
                    ex);
        }
    }
}
