package io.github.spike.myai.auth.application.query;

import io.github.spike.myai.auth.domain.model.AuditEventSearchCriteria;
import java.time.Instant;
import java.util.Set;

/**
 * 审计事件分页查询对象。
 * <p>
 * 封装审计事件分页查询的全部入参（筛选条件 + 分页参数），
 * 在 Record 紧凑构造器中完成业务校验，并提供方法将自身转换为领域层的
 * {@link AuditEventSearchCriteria} 搜索条件对象。
 * <p>
 * 校验规则：
 * <ul>
 *   <li>{@code limit} 必须在 1~100 之间，防止单次查询数据量过大</li>
 *   <li>{@code offset} 必须 ≥ 0</li>
 *   <li>{@code outcome} 若不为空，必须为 SUCCESS / FAILURE / DENIED 之一</li>
 *   <li>{@code occurredFrom} 不得晚于 {@code occurredTo}</li>
 * </ul>
 * 设计为不可变 Record，适合在分层架构中跨层传递。
 *
 * @param eventType    事件类型过滤，可为 {@code null}
 * @param actorUserId  操作者用户 ID 过滤，可为 {@code null}
 * @param targetType   目标类型过滤，可为 {@code null}
 * @param targetId     目标 ID 过滤，可为 {@code null}
 * @param outcome      结果代码过滤（SUCCESS / FAILURE / DENIED），可为 {@code null}
 * @param occurredFrom 起始时间（含），可为 {@code null}
 * @param occurredTo   结束时间（含），可为 {@code null}
 * @param limit        每页条数，范围 1~100
 * @param offset       分页偏移量，必须 ≥ 0
 * @author spike
 * @since 1.0.0
 */
public record ListAuditEventsQuery(
        String eventType,
        String actorUserId,
        String targetType,
        String targetId,
        String outcome,
        Instant occurredFrom,
        Instant occurredTo,
        int limit,
        int offset) {

    /** 合法的 outcome 值集合 */
    private static final Set<String> SUPPORTED_OUTCOMES = Set.of("SUCCESS", "FAILURE", "DENIED");

    /**
     * 紧凑构造器：在 Record 构造阶段执行业务校验。
     * <p>
     * 校验失败时抛出 {@link IllegalArgumentException}，
     * 由控制器统一捕获并映射为 HTTP 400。
     */
    public ListAuditEventsQuery {
        // 校验每页条数范围（1~100）
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        // 校验偏移量非负
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to 0");
        }
        // 校验 outcome 为合法枚举值（若提供）
        String normalizedOutcome = blankToNull(outcome);
        if (normalizedOutcome != null && !SUPPORTED_OUTCOMES.contains(normalizedOutcome)) {
            throw new IllegalArgumentException("outcome must be one of SUCCESS, FAILURE, DENIED");
        }
        // 校验时间范围合法性
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new IllegalArgumentException("occurredFrom must be less than or equal to occurredTo");
        }
    }

    /**
     * 转换为领域层搜索条件对象。
     * <p>
     * 将所有字符串字段通过 {@link #blankToNull} 处理，
     * 将空字符串和纯空白字符串统一转为 {@code null}，
     * 避免无效筛选条件污染 SQL。
     *
     * @return 领域层搜索条件对象
     */
    public AuditEventSearchCriteria toSearchCriteria() {
        return new AuditEventSearchCriteria(
                blankToNull(eventType),
                blankToNull(actorUserId),
                blankToNull(targetType),
                blankToNull(targetId),
                blankToNull(outcome),
                occurredFrom,
                occurredTo,
                limit,
                offset);
    }

    /**
     * 将空字符串或纯空白字符串转换为 {@code null}。
     * <p>
     * 用于清理前端可能传入的空筛选参数，
     * 确保 SQL 动态拼接时正确跳过无效过滤条件。
     *
     * @param value 原始字符串值
     * @return 若为空白则返回 {@code null}，否则返回原值
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
