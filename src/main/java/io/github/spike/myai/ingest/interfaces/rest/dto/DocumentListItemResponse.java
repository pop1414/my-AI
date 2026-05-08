package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.time.Instant;

/**
 * 文档列表项响应体（REST DTO）。
 *
 * <p>该 Record 是文档分页列表中的单条记录，
 * 包含文档的基本元数据与处理状态，用于前端列表展示。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code documentId} —— 文档全局唯一标识（UUID 格式），前端可用于详情查询与操作；</li>
 *   <li>{@code kbId} —— 所属知识库业务键，用于前端归类展示与知识库过滤；</li>
 *   <li>{@code filename} —— 原始上传文件名，用于前端列表的主要展示字段；</li>
 *   <li>{@code fileSize} —— 文件大小（字节），前端可格式化为 KB/MB 展示；</li>
 *   <li>{@code status} —— 文档处理状态（如 UPLOADED / PARSING / INDEXED / FAILED），
 *       前端据此渲染不同的状态标签；</li>
 *   <li>{@code failureReason} —— 失败原因，仅当 status 为 FAILED 时有值，
 *       用于前端展示错误提示；</li>
 *   <li>{@code createdAt} —— 文档上传受理时间（ISO-8601 格式）；</li>
 *   <li>{@code updatedAt} —— 文档最后更新时间（ISO-8601 格式）。</li>
 * </ul>
 *
 * <p>注意：该 Record 由 Java 编译器自动生成构造器、访问器、
 * {@code equals}、{@code hashCode} 及 {@code toString} 方法。
 *
 * @param documentId    文档唯一标识
 * @param kbId          所属知识库业务键
 * @param filename      原始文件名
 * @param fileSize      文件大小（字节）
 * @param status        处理状态
 * @param failureReason 失败原因（可为空）
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @author Spike
 * @since 1.0.0
 */
public record DocumentListItemResponse(
        String documentId,
        String kbId,
        String filename,
        long fileSize,
        String status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {
}
