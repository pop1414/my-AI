package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.monitoring.IngestMetrics;
import io.github.spike.myai.ingest.application.usecase.ProcessDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档处理执行应用服务（Application Service）。
 *
 * <p>该服务实现 {@link ProcessDocumentUseCase} 用例接口，
 * 负责执行文档入库的完整处理链路。
 *
 * <h3>处理链路</h3>
 * <ol>
 *   <li>读取源文件；</li>
 *   <li>解析纯文本；</li>
 *   <li>文本分块；</li>
 *   <li>向量写入；</li>
 *   <li>状态推进到 INDEXED（成功）或 FAILED / 重试（失败）。</li>
 * </ol>
 *
 * <h3>错误处理策略</h3>
 * <ul>
 *   <li><b>瞬时错误</b>：通过 {@link RetryPolicy} 判断，安排指数退避重试；</li>
 *   <li><b>致命错误</b>：直接标记为 FAILED，保留完整错误信息供排障。</li>
 * </ul>
 *
 * <p>所有状态变更均通过 CAS 乐观锁执行，防止并发覆盖。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ProcessDocumentApplicationService implements ProcessDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessDocumentApplicationService.class);

    /** 基础重试延迟（毫秒），用于指数退避算法的基数 */
    private static final long BASE_RETRY_DELAY_MILLIS = 1000L;
    /** 随机抖动上限（毫秒），防止大量任务同时重试造成压力峰值 */
    private static final long JITTER_MILLIS = 300L;

    /** 文档元数据仓储 */
    private final DocumentRepository documentRepository;
    /** 源文件存储 */
    private final DocumentSourceStorage sourceStorage;
    /** 文本解析器 */
    private final DocumentTextParser documentTextParser;
    /** 文本分块器 */
    private final DocumentChunker documentChunker;
    /** 文档处理中间产物存储 */
    private final DocumentProcessingArtifactStorage documentProcessingArtifactStorage;
    /** 矢量索引器 */
    private final DocumentVectorIndexer documentVectorIndexer;
    /** 重试策略判断器 */
    private final RetryPolicy retryPolicy;
    /** 业务指标监控 */
    private final IngestMetrics ingestMetrics;

    /**
     * 构造器注入：注入处理流程所需的各项基础设施和策略组件。
     *
     * @param documentRepository     文档元数据仓储
     * @param sourceStorage          源文件存储
     * @param documentTextParser     文本解析器
     * @param documentChunker        文本分块器
     * @param documentProcessingArtifactStorage 文档处理中间产物存储
     * @param documentVectorIndexer  矢量索引器
     * @param retryPolicy            重试策略判断器
     * @param ingestMetrics          业务指标监控
     */
    public ProcessDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage sourceStorage,
            DocumentTextParser documentTextParser,
            DocumentChunker documentChunker,
            DocumentProcessingArtifactStorage documentProcessingArtifactStorage,
            DocumentVectorIndexer documentVectorIndexer,
            RetryPolicy retryPolicy,
            IngestMetrics ingestMetrics) {
        this.documentRepository = documentRepository;
        this.sourceStorage = sourceStorage;
        this.documentTextParser = documentTextParser;
        this.documentChunker = documentChunker;
        this.documentProcessingArtifactStorage = documentProcessingArtifactStorage;
        this.documentVectorIndexer = documentVectorIndexer;
        this.retryPolicy = retryPolicy;
        this.ingestMetrics = ingestMetrics;
    }

    /**
     * 执行文档处理的核心逻辑。
     * 包括：读取文件、文本解析、内容切片、向量索引以及最后的状态更新。
     *
     * <p>该方法采用乐观锁（CAS）推进状态，确保同一时间只有一个处理流程能成功更新数据库。
     *
     * @param documentId 待处理的文档唯一标识 ID
     */
    @Override
    public void handle(DocumentId documentId) {
        String workspaceId = WorkspaceConstants.DEFAULT_WORKSPACE_ID;
        // 第一步：读取当前文档资产快照，后续所有处理都以该快照为准。
        Document document = documentRepository.findById(workspaceId, documentId).orElse(null);
        if (document == null) {
            log.warn("Skip process because document not found. documentId={}", documentId.value());
            return;
        }
        // 安全阀：只有处于 INGESTING 的资产才允许执行处理链路，避免状态错乱。
        if (document.status() != UploadStatus.INGESTING) {
            log.warn(
                    "Skip process because document status is not INGESTING. documentId={}, status={}",
                    documentId.value(),
                    document.status());
            return;
        }

        DocumentParseResult parseResult = null;
        try {
            // 步骤 1: 从存储系统中调取原始文件字节数组。
            byte[] sourceBytes = sourceStorage
                    .load(documentId, document.filename())
                    .orElseThrow(() -> new IllegalStateException("source file not found"));

            // 步骤 2: 解析出一期中间产物，并将 cleaned.md 主链结果落盘。
            parseResult = documentTextParser.parse(document.filename(), sourceBytes);
            documentProcessingArtifactStorage.save(documentId, parseResult);

            // 步骤 3: 分块（结构优先 + 长度兜底）。
            List<DocumentChunk> chunks = documentChunker.chunk(parseResult.cleanedMarkdown());
            if (chunks.isEmpty()) {
                throw new IllegalStateException("no chunks generated");
            }

            // 步骤 4: 调用 Embedding 模型并将生成的矢量存入向量数据库，以便后续语义搜索。
            documentVectorIndexer.index(document, chunks);

            // 步骤 5: 状态收口：仅当当前仍是 INGESTING 时，才允许推进到最终成功态 INDEXED。
            // 使用 CAS 确保在处理期间文档没有被外部（如删除操作）修改。
            boolean updated = documentRepository.markIndexed(
                    workspaceId,
                    documentId,
                    UploadStatus.INGESTING,
                    parseResult.processingMetadata(),
                    Instant.now());
            if (!updated) {
                log.warn("Status update to INDEXED skipped by CAS. documentId={}", documentId.value());
                return;
            }
            ingestMetrics.incrementProcessSuccess();
            log.info("Document processed successfully. documentId={}, chunks={}", documentId.value(), chunks.size());
        } catch (Exception ex) {
            // 异常处理逻辑：依据策略判断这次异常是“暂时的”还是“致命的”。
            RetryPolicy.RetryDecision decision = retryPolicy.decide(ex);
            Instant now = Instant.now();

            // 如果判断为可重试错误且未超过最大重试次数
            if (decision.transientError() && document.retryCount() < document.retryMax()) {
                int nextRetryCount = document.retryCount() + 1;
                // 计算下一次尝试的时间点，采用指数退避算法增加间隔。
                Instant nextRetryAt = now.plus(computeBackoff(nextRetryCount));

                // 将状态更新为待重试，并记录当前的错误代码和摘要。
                boolean updated = documentRepository.markRetry(
                        workspaceId,
                        documentId,
                        UploadStatus.INGESTING,
                        nextRetryCount,
                        nextRetryAt,
                        decision.errorCode(),
                        decision.errorMessage(),
                        now,
                        now);
                if (!updated) {
                    log.warn("Retry scheduling skipped by CAS. documentId={}", documentId.value());
                } else {
                    ingestMetrics.incrementRetryScheduled();
                    log.warn(
                            "Document processing failed, scheduled retry. documentId={}, status={}, retryCount={}, nextRetryAt={}",
                            documentId.value(),
                            UploadStatus.UPLOADED,
                            nextRetryCount,
                            nextRetryAt);
                }
                return;
            }

            // 如果不可重试或已达上限，则标记为最终失败态 FAILED。
            String reason = trimFailureReason(decision.errorMessage());
            // 非瞬时错误或超过最大重试次数时，直接进入 FAILED 并保留错误信息。
            boolean updated = documentRepository.markFailed(
                    workspaceId,
                    documentId,
                    UploadStatus.INGESTING,
                    reason,
                    parseResult != null ? parseResult.processingMetadata() : null,
                    decision.errorCode(),
                    decision.errorMessage(),
                    now,
                    now);
            if (!updated) {
                log.warn("Status update to FAILED skipped by CAS. documentId={}", documentId.value());
                return;
            }
            ingestMetrics.incrementProcessFailed();
            log.warn(
                    "Document processing failed. documentId={}, status={}, retryCount={}, reason={}",
                    documentId.value(),
                    UploadStatus.FAILED,
                    document.retryCount(),
                    reason,
                    ex);
        }
    }

    /**
     * 对失败原因进行截断处理。
     * 数据库字段通常有长度限制，且过长的错误堆栈不适合存放在状态表中。
     *
     * @param reason 原始错误消息
     * @return 截断后的安全简短消息
     */
    private static String trimFailureReason(String reason) {
        // 统一失败原因长度，避免异常栈/超长文本直接落库。
        if (reason == null || reason.isBlank()) {
            return "unknown processing error";
        }
        int limit = 500;
        if (reason.length() <= limit) {
            return reason;
        }
        return reason.substring(0, limit);
    }

    /**
     * 计算重试等待时间。
     * 采用指数退避算法：delay = base * 2^(retryCount-1) + jitter。
     *
     * @param retryCount 当前是第几次重试
     * @return 需要等待的时长对象
     */
    private static Duration computeBackoff(int retryCount) {
        // 指数退避：1s, 2s, 4s, 8s...；并加 0~300ms 随机抖动。
        long exponential = BASE_RETRY_DELAY_MILLIS * (1L << Math.max(0, retryCount - 1));
        long jitter = ThreadLocalRandom.current().nextLong(0, JITTER_MILLIS + 1);
        return Duration.ofMillis(exponential + jitter);
    }
}
