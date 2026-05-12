package io.github.spike.myai.ingest.domain.model;

import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;

/**
 * 文档聚合根（Aggregate Root）。
 *
 * <p>该对象表示一条“文档入库任务”的核心业务状态，包含：
 * <ul>
 *     <li>文档资产标识、知识库、文件信息</li>
 *     <li>文件哈希（用于上传幂等判断）</li>
 *     <li>处理状态与失败原因</li>
 *     <li>重试控制（次数、下一次重试时间、错误信息）</li>
 *     <li>重处理计数与分块版本</li>
 *     <li>创建时间与更新时间</li>
 * </ul>
 *
 * <p>设计意图：
 * <ul>
 *     <li>把与文档状态相关的规则集中在领域层，避免散落在控制器或基础设施中。</li>
 *     <li>后续扩展状态流转（如重试、回滚）时，优先在该聚合中演进。</li>
 * </ul>
 *
 * @param documentId           全局唯一的文档聚合标识ID（值对象）
 * @param workspaceId          所属工作区标识（当前固定为 {@code "default"}）
 * @param kbId                 文档归属的知识库标识 ID，用于数据隔离和路由
 * @param fileHash             原始文件内容的散列值(通常为 SHA-256)，用于去重、防重传和秒传判定
 * @param filename             用户上传的原始文件名称，用于界面展示与追溯
 * @param fileSize             物理文件的实际大小，单位：字节(bytes)
 * @param status               当前文档在解析入库(Ingest)流水线中所处的生命周期状态枚举
 * @param failureReason        当状态为失败时，用来保存失败概要说明
 * @param retryCount           当前已经历过的自动或被动重试执行次数
 * @param retryMax             系统或业务规定的允许最大重试重回次数上限
 * @param nextRetryAt          基于退避策略计算得出的下一次可被调度执行重试的时间点
 * @param lastErrorCode        上一次处理故障捕获到的内部系统或底层组件的异常特征码
 * @param lastErrorMessage     上一次处理故障捕获到的详细错误提示信息报文
 * @param lastErrorAt          上一次发生失败的确切时间戳记录
 * @param reprocessCount       人为或系统触发的针对本文档全量“重新处理（洗数据）”累加次数
 * @param reprocessRequestedAt 最近一次成功派发“重新处理任务”的请求发生时间点
 * @param splitVersion         应用在本文档上的文本分块或模型向量化策略版本（如v1/v2），用于检索时版本匹配和灰度
 * @param processingMetadata   文档处理结果元数据（JSON 字符串），作为 `processing_metadata` 字段的领域承载
 * @param createdAt            聚合根/文档条目首次落地创建时的持久化时间
 * @param updatedAt            聚合根/文档条目任何字段发生变更时的最后更新时间
 */
public record Document(
        DocumentId documentId,
        String workspaceId,
        String kbId,
        String fileHash,
        String filename,
        long fileSize,
        UploadStatus status,
        String failureReason,
        int retryCount,
        int retryMax,
        Instant nextRetryAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        int reprocessCount,
        Instant reprocessRequestedAt,
        String splitVersion,
        String processingMetadata,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 便利构造器：自动填充默认工作区标识。
     *
     * <p>当前为单工作区模式，调用方无需显式传递 {@code workspaceId}，
     * 该构造器自动使用 {@link WorkspaceConstants#DEFAULT_WORKSPACE_ID}。
     */
    public Document(
            DocumentId documentId,
            String kbId,
            String fileHash,
            String filename,
            long fileSize,
            UploadStatus status,
            String failureReason,
            int retryCount,
            int retryMax,
            Instant nextRetryAt,
            String lastErrorCode,
            String lastErrorMessage,
            Instant lastErrorAt,
            int reprocessCount,
            Instant reprocessRequestedAt,
            String splitVersion,
            String processingMetadata,
            Instant createdAt,
            Instant updatedAt) {
        this(
                documentId,
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                kbId,
                fileHash,
                filename,
                fileSize,
                status,
                failureReason,
                retryCount,
                retryMax,
                nextRetryAt,
                lastErrorCode,
                lastErrorMessage,
                lastErrorAt,
                reprocessCount,
                reprocessRequestedAt,
                splitVersion,
                processingMetadata,
                createdAt,
                updatedAt);
    }

    /**
     * 默认最大重试次数：3 次。
     */
    public static final int DEFAULT_RETRY_MAX = 3;

    /**
     * 默认文本分块策略版本。
     */
    public static final String DEFAULT_SPLIT_VERSION = "v1";

    /**
     * 记录数据契约：保证领域对象在被实例化构造后即刻处于合法安全的“有效状态”。
     * 防御性编程拦截系统脏数据流入。
     */
    public Document {
        // 1. documentId 必填校验
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        // 2. workspaceId 必填校验（便利构造器自动填充默认值）
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        // 3. kbId 必填校验
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        // 4. fileSize 非负校验
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        // 5. status 必填校验
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        // 6. retryCount 非负校验
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        // 7. retryMax 正数校验
        if (retryMax < 1) {
            throw new IllegalArgumentException("retryMax must be positive");
        }
        // 8. splitVersion 必填校验，用于区分向量版本
        if (splitVersion == null || splitVersion.isBlank()) {
            throw new IllegalArgumentException("splitVersion must not be blank");
        }
        // 9. 时间戳必填校验
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }
    }

    /**
     * 工厂方法：创建初始状态为 UPLOADED 的文档聚合根（使用默认工作区）。
     *
     * <p>便利重载，自动填充 {@link WorkspaceConstants#DEFAULT_WORKSPACE_ID}。
     * 返回的聚合根已预设默认重试参数（{@link #DEFAULT_RETRY_MAX}）
     * 与默认分块版本（{@link #DEFAULT_SPLIT_VERSION}）。
     *
     * @param documentId 文档唯一标识
     * @param kbId       关联的知识库 ID
     * @param fileHash   文件内容哈希（如 SHA-256）
     * @param filename   原始文件名
     * @param fileSize   文件大小（字节）
     * @param now        当前时间
     * @return 初始状态为 UPLOADED 的有效文档聚合
     */
    public static Document uploaded(
            DocumentId documentId,
            String kbId,
            String fileHash,
            String filename,
            long fileSize,
            Instant now) {
        return uploaded(documentId, WorkspaceConstants.DEFAULT_WORKSPACE_ID, kbId, fileHash, filename, fileSize, now);
    }

    /**
     * 工厂方法：创建初始状态为 UPLOADED 的文档聚合根（显式指定工作区）。
     *
     * <p>调用方需显式传入 {@code workspaceId}，适用于多工作区场景。
     * {@code fileHash} 不可为空，因其是后续幂等去重的关键依据。
     *
     * @param documentId  文档唯一标识
     * @param workspaceId 所属工作区标识
     * @param kbId        关联的知识库 ID
     * @param fileHash    文件内容哈希（不可为空）
     * @param filename    原始文件名
     * @param fileSize    文件大小（字节）
     * @param now         当前时间
     * @return 初始状态为 UPLOADED 的有效文档聚合
     */
    public static Document uploaded(
            DocumentId documentId,
            String workspaceId,
            String kbId,
            String fileHash,
            String filename,
            long fileSize,
            Instant now) {
        // fileHash 是幂等去重的关键依据，不可为空
        if (fileHash == null || fileHash.isBlank()) {
            throw new IllegalArgumentException("fileHash must not be blank");
        }
        return new Document(
                documentId,
                workspaceId,
                kbId,
                fileHash,
                filename,
                fileSize,
                UploadStatus.UPLOADED,     // 初始状态：已上传
                null,                       // 无失败原因
                0,                          // 重试计数归零
                DEFAULT_RETRY_MAX,          // 默认最大重试次数
                null,                       // 无下次重试时间
                null,                       // 无错误码
                null,                       // 无错误信息
                null,                       // 无错误时间
                0,                          // 重处理计数归零
                null,                       // 无重处理请求时间
                DEFAULT_SPLIT_VERSION,      // 默认分块版本
                null,                       // 初始无处理结果元数据
                now,                        // 创建时间
                now);                       // 更新时间（同创建时间）
    }

    /**
     * 进入"处理中（INGESTING）"状态。
     *
     * <p>当前状态为 UPLOADED 或 FAILED 时可调用，
     * 表示文档进入解析/分块/向量化的异步处理链路。
     *
     * @param at 更新时间
     * @return 新状态文档（不可变对象，返回新实例）
     */
    public Document markIngesting(Instant at) {
        return withStatus(UploadStatus.INGESTING, null, at);
    }

    /**
     * 标记"索引完成（INDEXED）"状态。
     *
     * <p>表示文档已完成全部解析入库流程，可被知识库检索命中。
     *
     * @param at 更新时间
     * @return 新状态文档（不可变对象，返回新实例）
     */
    public Document markIndexed(Instant at) {
        return withStatus(UploadStatus.INDEXED, null, at);
    }

    /**
     * 标记"处理失败（FAILED）"状态。
     *
     * <p>失败文档可被重处理（reprocess）或手动重试。
     *
     * @param reason 失败原因，用于排障与前端展示
     * @param at     更新时间
     * @return 新状态文档（不可变对象，返回新实例）
     */
    public Document markFailed(String reason, Instant at) {
        return withStatus(UploadStatus.FAILED, reason, at);
    }

    /**
     * 核心状态机辅助转换方法。
     * 因为 Java Record 的成员字段为不可变 (immutable)，在发生状态转移时不可直接就地修改，
     * 必须基于当前的属性创建一个属性拷贝的“全新 Document 实例”。
     * 被改变的字段主要是状态、失败原因及操作时间，其它的领域参数透传保持不变。
     *
     * @param newStatus        准备迁移到的目标状态枚举 (如 INGESTING, INDEXED)
     * @param newFailureReason 伴随异常产生时的新失败事由（成功场景传入 null 即可）
     * @param at               状态变迁的发生/覆盖时间
     * @return 一份携带最新状态信息的、在内存层面的新版文档聚合实例
     */
    private Document withStatus(UploadStatus newStatus, String newFailureReason, Instant at) {
        return new Document(
                documentId,              // 标识不变
                workspaceId,             // 工作区不变
                kbId,                    // 知识库归属不变
                fileHash,                // 文件哈希不变
                filename,                // 文件名不变
                fileSize,                // 文件大小不变
                newStatus,               // 新状态
                newFailureReason,        // 新失败原因
                retryCount,              // 重试计数不变
                retryMax,                // 最大重试次数不变
                nextRetryAt,             // 下次重试时间不变
                lastErrorCode,           // 错误码不变
                lastErrorMessage,        // 错误信息不变
                lastErrorAt,             // 错误时间不变
                reprocessCount,          // 重处理计数不变
                reprocessRequestedAt,    // 重处理请求时间不变
                splitVersion,            // 分块版本不变
                processingMetadata,      // 处理结果元数据默认透传
                createdAt,               // 创建时间保持不变
                at);                     // 更新时间刷新为当前时刻
    }
}
