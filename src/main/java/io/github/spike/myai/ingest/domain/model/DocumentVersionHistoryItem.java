package io.github.spike.myai.ingest.domain.model;

import java.time.Instant;

/**
 * 文档版本历史读模型项。
 *
 * <p>该模型直接服务于按 document 查询版本链的只读场景，
 * 只承载前端版本历史视图需要的稳定事实。
 * 数据来源于 ingest_documents 与 ingest_document_versions 两表的 JOIN 查询结果。
 *
 * <p>设计原则：
 * <ul>
 *   <li>不可变值对象（Record）—— 构造后状态不可变更；</li>
 *   <li>紧凑构造器全字段校验 —— 确保非法数据不会流入领域层；</li>
 *   <li>不包含任何行为方法 —— 纯数据载体，行为归属于领域服务或应用服务。</li>
 * </ul>
 *
 * @param documentId               文档资产标识（强类型 ID）
 * @param workspaceId              所属工作区 ID
 * @param kbId                     所属知识库 ID
 * @param latestVersionNumber      文档当前最新版本号（用于判定 isLatestVersion）
 * @param versionNumber            本条版本记录的版本号
 * @param versionOriginType        版本来源类型（UPLOAD / ROLLBACK 等）
 * @param rollbackFromVersionNumber 回滚来源版本号，非回滚版本时为 null
 * @param filename                 该版本对应的原始文件名
 * @param fileSize                 文件大小（字节）
 * @param status                   处理状态枚举（INDEXED / FAILED / PROCESSING 等）
 * @param failureReason            失败原因描述，仅 status=FAILED 时有值
 * @param createdByUserId          创建该版本的用户 ID，历史数据可能为空
 * @param createdByDisplayName     创建该版本的用户展示名，历史数据或用户缺失时可能为空
 * @param createdAt                版本创建时间
 * @param updatedAt                版本最后更新时间
 */
public record DocumentVersionHistoryItem(
        DocumentId documentId,
        String workspaceId,
        String kbId,
        int latestVersionNumber,
        int versionNumber,
        DocumentVersionOriginType versionOriginType,
        Integer rollbackFromVersionNumber,
        String filename,
        long fileSize,
        UploadStatus status,
        String failureReason,
        String createdByUserId,
        String createdByDisplayName,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 紧凑构造器：对所有字段进行防御性校验。
     *
     * <p>校验策略遵循"快速失败"原则（Fail-Fast）：
     * 任何字段不满足约束时立即抛出 {@link IllegalArgumentException}，
     * 防止脏数据在领域层传播。
     *
     * @throws IllegalArgumentException 当任一字段不符合领域约束时
     */
    public DocumentVersionHistoryItem {
        // ──── 标识类字段校验 ────
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }

        // ──── 版本号语义校验 ────
        // latestVersionNumber 必须为正整数，表示文档的当前最新版本
        if (latestVersionNumber < 1) {
            throw new IllegalArgumentException("latestVersionNumber must be positive");
        }
        // versionNumber 必须为正整数，每条版本记录至少从 1 开始
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }

        // ──── 版本来源类型校验 ────
        if (versionOriginType == null) {
            throw new IllegalArgumentException("versionOriginType must not be null");
        }

        // ──── 文件元数据校验 ────
        // 文件名不可为空，每条版本记录必须关联一个有效的文件名
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        // 文件大小不可为负数（0 表示空文件，属于合法值）
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }

        // ──── 状态字段校验 ────
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }

        // ──── 时间戳字段校验 ────
        // 创建时间和更新时间均为必填，由数据库自动维护
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }
    }
}
