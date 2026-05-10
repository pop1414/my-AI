package io.github.spike.myai.auth.application.result;

import java.util.List;

/**
 * 审计事件分页结果对象。
 * <p>
 * 用例层返回的分页查询结果，聚合当前页数据列表与分页元信息。
 * 设计为 Record，字段只读，天然线程安全。
 *
 * @param items  当前页审计事件列表
 * @param total  符合筛选条件的总记录数
 * @param limit  每页最大条数
 * @param offset 当前页偏移量
 * @author spike
 * @since 1.0.0
 */
public record AuditEventPageResult(
        List<AuditEventItemResult> items,
        long total,
        int limit,
        int offset) {
}
