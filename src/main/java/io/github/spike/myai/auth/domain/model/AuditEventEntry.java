package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 审计事件查询领域模型（只读视图）。
 * <p>
 * 专用于审计查询接口的投影数据，在保留审计原始字段的同时补充数据库自增主键 {@code auditEventId}，
 * 便于分页列表按主键降序实现稳定排序（相邻页不会因时间重复而产生分页漂移）。
 * <p>
 * 设计为不可变 Record，由仓储实现通过 JDBC RowMapper 从 {@code audit_events} 表查询构造。
 *
 * @param auditEventId  审计事件自增主键，用于稳定分页排序
 * @param workspaceId   所属工作区 ID
 * @param actorUserId   操作者用户唯一标识
 * @param actorUsername 操作者用户名
 * @param eventType     事件类型
 * @param targetType    操作目标类型
 * @param targetId      操作目标唯一标识
 * @param outcome       操作结果代码（SUCCESS / FAILURE / DENIED）
 * @param reason        失败原因代码
 * @param metadata      扩展元数据（JSON 字符串）
 * @param occurredAt    事件发生时间
 * @author spike
 * @since 1.0.0
 */
public record AuditEventEntry(
        long auditEventId,
        String workspaceId,
        String actorUserId,
        String actorUsername,
        String eventType,
        String targetType,
        String targetId,
        String outcome,
        String reason,
        String metadata,
        Instant occurredAt) {
}
