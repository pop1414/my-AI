package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.AuditEvent;

/**
 * 审计事件仓储端口（出站端口）。
 *
 * <p>定义审计事件的持久化契约，位于六边形架构的领域层。
 * 应用服务仅依赖此接口，不感知底层存储实现（如 JDBC、Elasticsearch 等）。
 *
 * <p>当前唯一实现：
 * {@link io.github.spike.myai.auth.infrastructure.persistence.JdbcAuditEventRepository}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface AuditEventRepository {

    /**
     * 持久化一条审计事件。
     *
     * <p>审计事件为只追加（append-only）数据，不支持修改或删除。
     * 实现应保证写入的原子性和可靠性。
     *
     * @param event 审计事件领域对象
     */
    void save(AuditEvent event);
}
