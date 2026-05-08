package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.time.Instant;
import java.util.Optional;

/**
 * 文档仓储端口（Domain Port / Repository Interface）。
 *
 * <p>该接口是六边形架构中的<b>领域端口</b>，定义领域层对文档元数据持久化的能力需求。
 * 领域层只声明需要什么操作，不关心底层实现是 PostgreSQL、MySQL 还是内存。
 *
 * <p>所有写操作均采用 CAS（Compare And Set）模式，通过 {@code expectedStatus}
 * 参数实现乐观锁，防止并发处理导致的状态覆盖。
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentRepository {

    /**
     * 保存文档聚合（新增或更新）。
     *
     * @param document 领域文档对象
     */
    void save(Document document);

    /**
     * 按文档 ID 查询文档聚合。
     *
     * @param workspaceId 工作区标识
     * @param documentId  文档 ID
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findById(String workspaceId, DocumentId documentId);

    /**
     * 按知识库和文件哈希查询文档，用于上传受理幂等。
     *
     * <p>排除已删除（{@code DELETED}）状态的文档。
     *
     * @param workspaceId 工作区标识
     * @param kbId        知识库 ID
     * @param fileHash    文件内容哈希（SHA-256 十六进制）
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findByKbIdAndFileHash(String workspaceId, String kbId, String fileHash);

    /**
     * 查找最早可处理的一条文档资产记录。
     *
     * <p>筛选条件（实现侧约束）：
     * <ul>
     *   <li>status = UPLOADED</li>
     *   <li>next_retry_at 为空或已到期</li>
     *   <li>retry_count &lt; retry_max</li>
     * </ul>
     *
     * @param now 当前时间
     * @return 查询结果，未命中时返回空
     */
    /**
     * 查找最早可处理的一条文档（用于异步调度）。
     *
     * <p>筛选条件（实现侧约束）：
     * <ul>
     *   <li>status = UPLOADED</li>
     *   <li>next_retry_at 为空或已到期</li>
     *   <li>retry_count &lt; retry_max</li>
     * </ul>
     *
     * @param workspaceId 工作区标识
     * @param now         当前时间
     * @return 查询结果，未命中时返回空
     */
    Optional<Document> findOldestReadyForProcessing(String workspaceId, Instant now);

    /**
     * 条件更新状态（CAS）。
     *
     * <p>仅当数据库当前状态与 expectedStatus 一致时才会更新成功。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望当前状态
     * @param targetStatus   目标状态
     * @param failureReason  失败原因（非失败状态可传 null）
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean compareAndSetStatus(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            UploadStatus targetStatus,
            String failureReason,
            Instant updatedAt);

    /**
     * 标记处理成功。
     *
     * <p>同时清理失败原因与重试信息，确保成功状态干净。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean markIndexed(String workspaceId, DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt);

    /**
     * 标记处理失败。
     *
     * <p>用于“非瞬时错误”或“超过最大重试次数”的场景。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态
     * @param failureReason  失败原因
     * @param errorCode      错误码
     * @param errorMessage   错误信息
     * @param errorAt        错误发生时间
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean markFailed(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String failureReason,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt);

    /**
     * 标记瞬时失败并安排重试。
     *
     * <p>该方法会将状态回退到 UPLOADED，并写入 next_retry_at。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态
     * @param retryCount     最新重试次数
     * @param nextRetryAt    下一次重试时间
     * @param errorCode      错误码
     * @param errorMessage   错误信息
     * @param errorAt        错误发生时间
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean markRetry(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            int retryCount,
            Instant nextRetryAt,
            String errorCode,
            String errorMessage,
            Instant errorAt,
            Instant updatedAt);

    /**
     * 请求重处理。
     *
     * <p>用于 FAIL/INDEXED 触发重处理，通常会执行 splitVersion++。
     *
     * @param workspaceId     工作区标识
     * @param documentId      文档资产 ID
     * @param expectedStatus  期望状态（FAILED/INDEXED）
     * @param newSplitVersion 新的分块版本
     * @param requestedAt     请求时间
     * @return 更新是否成功
     */
    boolean requestReprocess(
            String workspaceId,
            DocumentId documentId,
            UploadStatus expectedStatus,
            String newSplitVersion,
            Instant requestedAt);

    /**
     * 将文档状态推进为 DELETING。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param expectedStatus 期望状态（UPLOADED/FAILED/INDEXED）
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean markDeleting(String workspaceId, DocumentId documentId, UploadStatus expectedStatus, Instant updatedAt);

    /**
     * 将文档状态推进为 DELETED。
     *
     * <p>仅当文档当前状态为 DELETING 时才执行，确保删除流程的原子性。
     *
     * @param workspaceId 工作区标识
     * @param documentId  文档资产 ID
     * @param updatedAt   更新时间
     * @return 更新是否成功
     */
    boolean markDeleted(String workspaceId, DocumentId documentId, Instant updatedAt);

    /**
     * 删除流程失败时回滚 DELETING 状态到原状态。
     *
     * <p>用于删除失败时的补偿操作，恢复文档到可用状态。
     *
     * @param workspaceId    工作区标识
     * @param documentId     文档资产 ID
     * @param rollbackStatus 回滚目标状态（进入删除前的状态）
     * @param updatedAt      更新时间
     * @return 更新是否成功
     */
    boolean rollbackDeleting(String workspaceId, DocumentId documentId, UploadStatus rollbackStatus, Instant updatedAt);
}
