package io.github.spike.myai.auth.domain.port;

import io.github.spike.myai.auth.domain.model.AuditEventPage;
import io.github.spike.myai.auth.domain.model.AuditEventSearchCriteria;

/**
 * 审计事件查询仓储端口。
 * <p>
 * 定义治理接口所需的审计事件分页查询能力。
 * 与写入侧的 {@link io.github.spike.myai.auth.domain.port.AuditEventRepository} 分离，
 * 遵循 CQRS 原则，读写模型各司其职。
 * <p>
 * 实现类需支持按多个维度（事件类型、操作者、目标类型、结果代码、时间范围）动态组合筛选条件。
 *
 * @author spike
 * @since 1.0.0
 */
public interface AuditEventQueryRepository {

    /**
     * 按工作区与筛选条件查询审计事件分页结果。
     * <p>
     * 仅返回当前工作区下的审计事件，实现租户级数据隔离。
     * 筛选条件中为 {@code null} 的维度不参与过滤。
     *
     * @param workspaceId 工作区 ID，不能为空
     * @param criteria    查询条件，所有字段均为可选筛选维度，不能为 {@code null}
     * @return 审计事件分页结果（含当前页列表和总数）
     */
    AuditEventPage findPage(String workspaceId, AuditEventSearchCriteria criteria);
}
