package io.github.spike.myai.auth.domain.model;

import java.time.Instant;

/**
 * 审计事件领域模型。
 *
 * <p>记录系统中安全相关的操作事件（如登录成功、登录失败、账户锁定等），
 * 用于事后审计追踪和安全分析。每个事件包含操作主体、操作目标、
 * 结果（SUCCESS / FAILURE）及失败原因等完整上下文。
 *
 * <p>使用 Java {@code record} 保证不可变性和数据完整性，
 * 一旦创建不可修改，确保审计日志的可信性。
 *
 * <p>推荐通过静态工厂方法创建实例，而非直接调用规范构造器：
 * <ul>
 *   <li>{@link #success} —— 创建成功事件（outcome=SUCCESS, reason=空）；</li>
 *   <li>{@link #failure} —— 创建失败事件（outcome=FAILURE, reason=指定原因）。</li>
 * </ul>
 *
 * @param workspaceId   工作空间 ID（可为 {@code null}，如用户不存在时）
 * @param actorUserId   操作主体用户 ID
 * @param actorUsername 操作主体用户名
 * @param eventType     事件类型代码（如 {@code LOGIN_SUCCESS}、{@code LOGIN_FAILURE}）
 * @param targetType    操作目标类型（当前固定为 {@code "USER"}）
 * @param targetId      操作目标 ID（通常与 actorUserId 相同）
 * @param outcome       结果代码（{@code "SUCCESS"} 或 {@code "FAILURE"}）
 * @param reason        失败原因代码（如 {@code "BAD_CREDENTIALS"}、{@code "ACCOUNT_LOCKED"}），成功时为空字符串
 * @param metadata      扩展元数据（JSON 字符串，当前固定为 {@code "{}"}）
 * @param occurredAt    事件发生时间戳（UTC）
 * @author spike
 * @since 1.0.0
 */
public record AuditEvent(
        /** 工作空间 ID，用户不存在时可能为 {@code null} */
        String workspaceId,
        /** 操作主体用户 ID */
        String actorUserId,
        /** 操作主体用户名 */
        String actorUsername,
        /** 事件类型代码，如 LOGIN_SUCCESS / LOGIN_FAILURE */
        String eventType,
        /** 操作目标类型，当前固定为 "USER" */
        String targetType,
        /** 操作目标 ID，通常与操作主体 ID 相同 */
        String targetId,
        /** 结果代码，SUCCESS 或 FAILURE */
        String outcome,
        /** 失败原因代码，成功时为空字符串 */
        String reason,
        /** 扩展元数据，JSON 格式，当前固定为 "{}" */
        String metadata,
        /** 事件发生时间戳（UTC） */
        Instant occurredAt) {

    /**
     * 创建成功审计事件的静态工厂方法。
     *
     * <p>预设 outcome 为 {@code "SUCCESS"}，reason 为空字符串，
     * targetType 为 {@code "USER"}，targetId 与 actorUserId 相同，
     * metadata 为空 JSON 对象。
     *
     * @param workspaceId   工作空间 ID
     * @param actorUserId   操作主体用户 ID
     * @param actorUsername 操作主体用户名
     * @param eventType     事件类型代码（如 {@code LOGIN_SUCCESS}）
     * @param occurredAt    事件发生时间戳
     * @return 成功审计事件实例
     */
    public static AuditEvent success(
            String workspaceId,
            String actorUserId,
            String actorUsername,
            String eventType,
            Instant occurredAt) {
        // 构建成功事件：outcome=SUCCESS，reason 为空，元数据为空 JSON
        return new AuditEvent(
                workspaceId,
                actorUserId,
                actorUsername,
                eventType,
                "USER",
                actorUserId,
                "SUCCESS",
                "",
                "{}",
                occurredAt);
    }

    /**
     * 创建失败审计事件的静态工厂方法。
     *
     * <p>预设 outcome 为 {@code "FAILURE"}，携带具体失败原因，
     * targetType 为 {@code "USER"}，targetId 与 actorUserId 相同，
     * metadata 为空 JSON 对象。
     *
     * @param workspaceId   工作空间 ID（可为 {@code null}）
     * @param actorUserId   操作主体用户 ID（可为 {@code null}，如用户不存在时）
     * @param actorUsername 操作主体用户名
     * @param eventType     事件类型代码（如 {@code LOGIN_FAILURE}）
     * @param reason        失败原因代码（如 {@code BAD_CREDENTIALS}、{@code ACCOUNT_LOCKED}）
     * @param occurredAt    事件发生时间戳
     * @return 失败审计事件实例
     */
    public static AuditEvent failure(
            String workspaceId,
            String actorUserId,
            String actorUsername,
            String eventType,
            String reason,
            Instant occurredAt) {
        // 构建失败事件：outcome=FAILURE，携带具体失败原因
        return new AuditEvent(
                workspaceId,
                actorUserId,
                actorUsername,
                eventType,
                "USER",
                actorUserId,
                "FAILURE",
                reason,
                "{}",
                occurredAt);
    }
}
