package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 审计事件查询条件领域模型。
 * <p>
 * 封装审计事件分页查询的全部筛选条件，由
 * {@link io.github.spike.myai.auth.application.query.ListAuditEventsQuery#toSearchCriteria()}
 * 转换而来，字段值为 {@code null} 表示该维度不做筛选。
 * 设计为不可变 Record。
 *
 * @param eventType    事件类型过滤（如 WORKSPACE_MEMBER_ROLE_UPDATED），{@code null} 表示不过滤
 * @param actorUserId  操作者用户 ID 过滤，{@code null} 表示不过滤
 * @param targetType   目标类型过滤（如 WORKSPACE_MEMBERSHIP），{@code null} 表示不过滤
 * @param targetId     目标 ID 过滤，{@code null} 表示不过滤
 * @param outcome      结果代码过滤（SUCCESS / FAILURE / DENIED），{@code null} 表示不过滤
 * @param occurredFrom 起始时间（含），{@code null} 表示不限制下界
 * @param occurredTo   结束时间（含），{@code null} 表示不限制上界
 * @param limit        每页最大条数
 * @param offset       分页偏移量
 * @author spike
 * @since 1.0.0
 */
public record AuditEventSearchCriteria(
        String eventType,
        String actorUserId,
        String targetType,
        String targetId,
        String outcome,
        Instant occurredFrom,
        Instant occurredTo,
        int limit,
        int offset) {
}
