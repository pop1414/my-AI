package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.query.ListAuditEventsQuery;
import io.github.spike.myai.auth.application.result.AuditEventItemResult;
import io.github.spike.myai.auth.application.result.AuditEventPageResult;
import io.github.spike.myai.auth.application.usecase.ListAuditEventsUseCase;
import io.github.spike.myai.auth.domain.port.AuditEventQueryRepository;
import org.springframework.stereotype.Service;

/**
 * 查询审计事件分页列表应用服务。
 * <p>
 * 实现 {@link ListAuditEventsUseCase} 用例，负责以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>将查询对象转换为领域搜索条件</li>
 *   <li>通过仓储层执行分页查询（仅查询当前工作区数据）</li>
 *   <li>将领域分页结果转换为用例层分页结果</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class ListAuditEventsApplicationService implements ListAuditEventsUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 审计事件查询仓储 */
    private final AuditEventQueryRepository auditEventQueryRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService      授权服务
     * @param auditEventQueryRepository 审计事件查询仓储
     */
    public ListAuditEventsApplicationService(
            AuthorizationService authorizationService,
            AuditEventQueryRepository auditEventQueryRepository) {
        this.authorizationService = authorizationService;
        this.auditEventQueryRepository = auditEventQueryRepository;
    }

    /**
     * 执行审计事件分页查询用例。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验当前用户的工作区管理权限</li>
     *   <li>将查询对象转换为领域搜索条件（含空白→null 清洗）</li>
     *   <li>调用仓储层执行分页查询（限定工作区范围）</li>
     *   <li>逐条将领域条目映射为用例层结果并组装分页对象</li>
     * </ol>
     *
     * @param query 分页查询对象，已在构造阶段完成参数校验
     * @return 分页查询结果
     */
    @Override
    public AuditEventPageResult handle(ListAuditEventsQuery query) {
        // Step 1: 校验工作区管理权限
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // Step 2: 将查询对象转为领域搜索条件，调用仓储层分页查询
        var page = auditEventQueryRepository.findPage(currentUser.workspaceId(), query.toSearchCriteria());
        // Step 3: 逐条映射领域条目为用例层结果，组装分页对象
        return new AuditEventPageResult(
                page.items().stream()
                        .map(item -> new AuditEventItemResult(
                                item.auditEventId(),
                                item.workspaceId(),
                                item.actorUserId(),
                                item.actorUsername(),
                                item.eventType(),
                                item.targetType(),
                                item.targetId(),
                                item.outcome(),
                                item.reason(),
                                item.metadata(),
                                item.occurredAt()))
                        .toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
