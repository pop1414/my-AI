package io.github.spike.myai.ingest.domain.model;

import java.time.Instant;

/**
 * 文档列表读模型结果项（Read Model / View）。
 *
 * <p>该 Record 是文档列表查询的单条结果视图，
 * 由仓储层通过 SQL 投影直接构造，不经由聚合根。
 * 与写模型 {@link Document} 的区别在于：
 * <ul>
 *   <li>不包含完整的领域行为（如状态机转换），仅为数据载体；</li>
 *   <li>直接暴露 {@link UploadStatus} 枚举与 {@link DocumentId} 值对象；</li>
 *   <li>不含内部审计字段（如处理重试次数、向量存储路径等）。</li>
 * </ul>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code documentId} —— 文档唯一标识（值对象，不可为 {@code null}）；</li>
 *   <li>{@code kbId} —— 所属知识库业务键；</li>
 *   <li>{@code filename} —— 原始上传文件名；</li>
 *   <li>{@code fileSize} —— 文件大小（字节）；</li>
 *   <li>{@code status} —— 当前处理状态（不可为 {@code null}）；</li>
 *   <li>{@code failureReason} —— 失败原因（仅 FAILED 状态时有值）；</li>
 *   <li>{@code createdAt} —— 上传受理时间；</li>
 *   <li>{@code updatedAt} —— 最后更新时间。</li>
 * </ul>
 *
 * @param documentId    文档唯一标识
 * @param kbId          所属知识库业务键
 * @param filename      原始文件名
 * @param fileSize      文件大小（字节）
 * @param status        当前处理状态
 * @param failureReason 失败原因（可为 {@code null}）
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @author Spike
 * @since 1.0.0
 */
public record DocumentListItem(
        DocumentId documentId,
        String kbId,
        String filename,
        long fileSize,
        UploadStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 紧凑构造器：确保数据库投影结果的核心字段有效。
     *
     * <p>校验范围限于不可能合法为空的字段（标识、状态、时间戳），
     * 文件名、文件大小等字段由数据库 NOT NULL 约束保证，不在此重复校验。
     *
     * @throws IllegalArgumentException 当核心字段为 {@code null} 时
     */
    public DocumentListItem {
        // 文档标识不可为空（数据库投影结果的基本保证）
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        // kbId 不可为空，知识库归属是文档的基本属性
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        // 状态不可为空，每条文档必须有一个明确的状态
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        // 时间戳不可为空（由 SQL DEFAULT / NOT NULL 保证）
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }
    }
}
