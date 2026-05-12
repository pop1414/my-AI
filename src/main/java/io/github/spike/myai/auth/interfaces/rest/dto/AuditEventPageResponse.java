package io.github.spike.myai.auth.interfaces.rest.dto;

import java.util.List;

/**
 * 审计事件分页响应 DTO。
 * <p>
 * 用于 {@code GET /api/v1/admin/audit-events} 接口的 JSON 响应体。
 * 由控制器从 {@link io.github.spike.myai.auth.application.result.AuditEventPageResult} 转换而来。
 *
 * @param items  当前页审计事件列表
 * @param total  符合筛选条件的总记录数
 * @param limit  每页最大条数
 * @param offset 当前页偏移量
 * @author spike
 * @since 1.0.0
 */
public record AuditEventPageResponse(
        List<AuditEventResponse> items,
        long total,
        int limit,
        int offset) {
}
