package io.github.spike.myai.auth.interfaces.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * 审计事件响应 DTO。
 * <p>
 * 用于审计事件分页列表接口的 JSON 响应项。
 * 由控制器的 {@code toResponse} 方法从 {@link io.github.spike.myai.auth.application.result.AuditEventItemResult} 转换而来。
 * <p>
 * 注意：{@code metadata} 字段在此处为 {@link com.fasterxml.jackson.databind.JsonNode} 类型，
 * 由控制器通过 {@link com.fasterxml.jackson.databind.ObjectMapper#readTree} 将数据库中的 JSON 字符串
 * 解析为结构化 JSON 对象后再填充，确保 API 响应的 {@code metadata} 是嵌套 JSON 对象而非字符串。
 *
 * @param auditEventId  审计事件自增主键
 * @param workspaceId   所属工作区 ID
 * @param actorUserId   操作者用户唯一标识
 * @param actorUsername 操作者用户名
 * @param eventType     事件类型
 * @param targetType    操作目标类型
 * @param targetId      操作目标唯一标识
 * @param outcome       操作结果代码（SUCCESS / FAILURE / DENIED）
 * @param reason        失败原因代码
 * @param metadata      扩展元数据（已解析为 JSON 对象）
 * @param occurredAt    事件发生时间
 * @author spike
 * @since 1.0.0
 */
public record AuditEventResponse(
        long auditEventId,
        String workspaceId,
        String actorUserId,
        String actorUsername,
        String eventType,
        String targetType,
        String targetId,
        String outcome,
        String reason,
        JsonNode metadata,
        Instant occurredAt) {
}
