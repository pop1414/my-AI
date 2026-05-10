package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.query.ListAuditEventsQuery;
import io.github.spike.myai.auth.application.result.AuditEventPageResult;

/**
 * 查询审计事件分页列表用例。
 * <p>
 * 定义对当前工作区审计事件进行分页查询和筛选的业务边界。
 * 通过 {@link ListAuditEventsQuery} 对象传入筛选条件和分页参数，
 * 用例内部完成权限校验后执行查询。
 * <p>
 * 实现类需完成以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>将查询对象转换为领域搜索条件</li>
 *   <li>通过仓储层执行分页查询</li>
 *   <li>将领域分页结果转换为用例层分页结果</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
public interface ListAuditEventsUseCase {

    /**
     * 执行审计事件分页查询用例。
     *
     * @param query 分页查询对象，包含筛选条件和分页参数，不能为 {@code null}
     * @return 分页查询结果
     * @throws IllegalArgumentException 当查询对象中的参数校验失败时抛出
     */
    AuditEventPageResult handle(ListAuditEventsQuery query);
}
