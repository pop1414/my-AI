package io.github.spike.myai.auth.domain.model;

import java.util.List;

/**
 * 审计事件分页结果领域模型。
 * <p>
 * 由仓储层返回的分页查询结果，供用例层消费并转换为 {@link io.github.spike.myai.auth.application.result.AuditEventPageResult}。
 * 设计为不可变 Record。
 *
 * @param items  当前页审计事件列表
 * @param total  符合筛选条件的总记录数
 * @param limit  每页最大条数
 * @param offset 当前页偏移量
 * @author spike
 * @since 1.0.0
 */
public record AuditEventPage(
        List<AuditEventEntry> items,
        long total,
        int limit,
        int offset) {
}
