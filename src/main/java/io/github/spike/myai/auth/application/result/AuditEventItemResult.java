package io.github.spike.myai.auth.application.result;

import java.time.Instant;

/**
 * 审计事件列表项结果对象。
 * <p>
 * 用例层返回的单条审计事件数据传输对象，
 * 包含审计事件全量字段，供分页列表展示使用。
 * 设计为 Record，字段只读，天然线程安全。
 *
 * @param auditEventId  审计事件自增主键
 * @param workspaceId   所属工作区 ID
 * @param actorUserId   操作者用户唯一标识
 * @param actorUsername 操作者用户名（登录名）
 * @param eventType     事件类型（如 WORKSPACE_MEMBER_ROLE_UPDATED）
 * @param targetType    操作目标类型（如 WORKSPACE_MEMBERSHIP）
 * @param targetId      操作目标唯一标识
 * @param outcome       操作结果代码（如 SUCCESS / FAILURE / DENIED）
 * @param reason        失败原因代码
 * @param metadata      扩展元数据（JSON 字符串格式）
 * @param occurredAt    事件发生时间
 * @author spike
 * @since 1.0.0
 */
public record AuditEventItemResult(
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
