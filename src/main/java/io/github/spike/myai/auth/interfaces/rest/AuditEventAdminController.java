package io.github.spike.myai.auth.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.auth.application.query.ListAuditEventsQuery;
import io.github.spike.myai.auth.application.result.AuditEventItemResult;
import io.github.spike.myai.auth.application.usecase.ListAuditEventsUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.AuditEventPageResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.AuditEventResponse;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 审计事件治理 REST 控制器。
 * <p>
 * 提供审计事件分页查询的 HTTP 接口。
 * 支持按以下维度组合筛选：
 * <ul>
 *   <li>事件类型（eventType）</li>
 *   <li>操作者用户 ID（actorUserId）</li>
 *   <li>目标类型（targetType）</li>
 *   <li>目标 ID（targetId）</li>
 *   <li>结果代码（outcome：SUCCESS / FAILURE / DENIED）</li>
 *   <li>时间范围（occurredFrom / occurredTo，ISO 8601 格式）</li>
 * </ul>
 * 所有筛选参数均为可选，默认返回当前工作区所有审计事件的前 20 条。
 * 权限校验由下游用例层完成。
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
public class AuditEventAdminController {

    /** 查询审计事件分页列表用例 */
    private final ListAuditEventsUseCase listAuditEventsUseCase;
    /** Jackson ObjectMapper，用于解析 metadata JSON 字符串为结构化对象 */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入所需依赖。
     *
     * @param listAuditEventsUseCase 审计事件查询用例
     * @param objectMapper           Jackson ObjectMapper
     */
    public AuditEventAdminController(
            ListAuditEventsUseCase listAuditEventsUseCase,
            ObjectMapper objectMapper) {
        this.listAuditEventsUseCase = listAuditEventsUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询当前工作区的审计事件。
     * <p>
     * 所有筛选参数均为可选。时间参数使用 ISO 8601 格式（如 {@code 2026-05-10T00:00:00Z}）。
     * 参数校验由 {@link ListAuditEventsQuery} 紧凑构造器在对象创建阶段完成，
     * 校验失败抛出 {@link IllegalArgumentException} → 映射为 HTTP 400。
     *
     * @param eventType    事件类型过滤（可选）
     * @param actorUserId  操作者用户 ID 过滤（可选）
     * @param targetType   目标类型过滤（可选）
     * @param targetId     目标 ID 过滤（可选）
     * @param outcome      结果代码过滤（可选）
     * @param occurredFrom 起始时间 ISO 8601 格式（可选）
     * @param occurredTo   结束时间 ISO 8601 格式（可选）
     * @param limit        每页条数，默认 20
     * @param offset       分页偏移量，默认 0
     * @return 审计事件分页响应
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public AuditEventPageResponse listAuditEvents(
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "actorUserId", required = false) String actorUserId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "occurredFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(value = "occurredTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        try {
            // 构造查询对象（内部完成参数校验），委托用例层执行
            var result = listAuditEventsUseCase.handle(new ListAuditEventsQuery(
                    eventType,
                    actorUserId,
                    targetType,
                    targetId,
                    outcome,
                    occurredFrom,
                    occurredTo,
                    limit,
                    offset));
            // 将用例层结果映射为 REST 响应 DTO
            return new AuditEventPageResponse(
                    result.items().stream().map(this::toResponse).toList(),
                    result.total(),
                    result.limit(),
                    result.offset());
        } catch (IllegalArgumentException ex) {
            // 参数校验失败 → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将用例层单条审计事件结果转换为 REST 响应 DTO。
     * <p>
     * 关键处理：将数据库中的 {@code metadata} JSON 字符串通过 {@link #parseMetadata}
     * 解析为 {@link JsonNode} 对象，确保 API 返回的 {@code metadata} 为嵌套 JSON 结构而非字符串。
     *
     * @param item 用例层审计事件条目
     * @return REST 响应对象
     */
    private AuditEventResponse toResponse(AuditEventItemResult item) {
        return new AuditEventResponse(
                item.auditEventId(),
                item.workspaceId(),
                item.actorUserId(),
                item.actorUsername(),
                item.eventType(),
                item.targetType(),
                item.targetId(),
                item.outcome(),
                item.reason(),
                // 将 JSON 字符串解析为结构化 JsonNode
                parseMetadata(item.metadata()),
                item.occurredAt());
    }

    /**
     * 将 metadata JSON 字符串解析为 {@link JsonNode}。
     * <p>
     * 若字符串为 {@code null} 或空白，则返回空 JSON 对象 {@code {}}，
     * 确保 API 响应的 {@code metadata} 字段始终为合法 JSON。
     *
     * @param metadata 数据库中的 metadata JSON 字符串
     * @return 解析后的 JsonNode
     * @throws IllegalStateException 当 metadata 内容不是合法 JSON 时抛出（属数据异常）
     */
    private JsonNode parseMetadata(String metadata) {
        try {
            // null 或空字符串时返回空 JSON 对象，保证响应格式一致性
            return objectMapper.readTree(metadata == null || metadata.isBlank() ? "{}" : metadata);
        } catch (JsonProcessingException ex) {
            // 数据库中的 metadata 损坏，属系统异常
            throw new IllegalStateException("invalid audit event metadata", ex);
        }
    }
}
