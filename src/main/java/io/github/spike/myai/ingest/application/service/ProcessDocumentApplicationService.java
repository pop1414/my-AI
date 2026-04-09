package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.usecase.ProcessDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 文档处理执行应用服务。
 *
 * <p>执行链路：
 * <ul>
 *     <li>读取源文件</li>
 *     <li>解析纯文本</li>
 *     <li>文本分块</li>
 *     <li>向量写入</li>
 *     <li>状态推进到 INDEXED/FAILED</li>
 * </ul>
 */
@Service
public class ProcessDocumentApplicationService implements ProcessDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessDocumentApplicationService.class);
    /**
     * 基础重试延迟时间（毫秒），用于指数退避算法的基数。
     */
    private static final long BASE_RETRY_DELAY_MILLIS = 1000L;
    /**
     * 随机抖动上限（毫秒），防止大量任务在同一时刻重试造成系统压力峰值。
     */
    private static final long JITTER_MILLIS = 300L;

    private final DocumentRepository documentRepository;
    private final DocumentSourceStorage sourceStorage;
    private final DocumentTextParser documentTextParser;
    private final DocumentChunker documentChunker;
    private final DocumentVectorIndexer documentVectorIndexer;
    private final RetryPolicy retryPolicy;

    public ProcessDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage sourceStorage,
            DocumentTextParser documentTextParser,
            DocumentChunker documentChunker,
            DocumentVectorIndexer documentVectorIndexer,
            RetryPolicy retryPolicy) {
        this.documentRepository = documentRepository;
        this.sourceStorage = sourceStorage;
        this.documentTextParser = documentTextParser;
        this.documentChunker = documentChunker;
        this.documentVectorIndexer = documentVectorIndexer;
        this.retryPolicy = retryPolicy;
    }

    /**
     * 执行文档处理的核心逻辑。
     * 包括：读取文件、文本解析、内容切片、向量索引以及最后的状态更新。
     *
     * @param documentId 待处理的文档唯一标识 ID
     */
    @Override
    public void handle(DocumentId documentId) {
        // 第一步：读取当前文档资产快照，后续所有处理都以该快照为准。
        Document document = documentRepository.findById(documentId).orElse(null);
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

        try {
            // 1) 读取源文件（上传阶段已落盘）；不存在时进入 FAILED。
            byte[] sourceBytes = sourceStorage
                    .load(documentId, document.filename())
                    .orElseThrow(() -> new IllegalStateException("source file not found"));
            // 2) 解析为纯文本（V1 先支持文本类文件）。
            String text = documentTextParser.parse(document.filename(), sourceBytes);
            // 3) 分块（结构优先 + 长度兜底）。
            List<DocumentChunk> chunks = documentChunker.chunk(text);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("no chunks generated");
            }
            // 4) 向量写入（内部保证幂等写入语义）。
            documentVectorIndexer.index(document, chunks);

            // 5) 状态收口：仅当当前仍是 INGESTING 时，才允许推进到 INDEXED。
            // 若 CAS 失败，说明状态已被其它流程改变，此处不再强写。
            boolean updated = documentRepository.markIndexed(documentId, UploadStatus.INGESTING, Instant.now());
            if (!updated) {
                log.warn("Status update to INDEXED skipped by CAS. documentId={}", documentId.value());
                return;
            }
            log.info("Document processed successfully. documentId={}, chunks={}", documentId.value(), chunks.size());
        } catch (Exception ex) {
            // 依据策略判断“是否瞬时错误”，决定进入重试还是直接失败。
            RetryPolicy.RetryDecision decision = retryPolicy.decide(ex);
            Instant now = Instant.now();
            if (decision.transientError() && document.retryCount() < document.retryMax()) {
                int nextRetryCount = document.retryCount() + 1;
                // 指数退避 + jitter，避免并发失败时集体同时重试。
                Instant nextRetryAt = now.plus(computeBackoff(nextRetryCount));
                boolean updated = documentRepository.markRetry(
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
                    log.warn(
                            "Document processing failed, scheduled retry. documentId={}, retryCount={}, nextRetryAt={}",
                            documentId.value(),
                            nextRetryCount,
                            nextRetryAt);
                }
                return;
            }
            // 失败收口：统一截断失败原因后写入 FAILED，避免错误消息过长污染存储。
            String reason = trimFailureReason(decision.errorMessage());
            // 非瞬时错误或超过最大重试次数时，直接进入 FAILED 并保留错误信息。
            boolean updated = documentRepository.markFailed(
                    documentId,
                    UploadStatus.INGESTING,
                    reason,
                    decision.errorCode(),
                    decision.errorMessage(),
                    now,
                    now);
            if (!updated) {
                log.warn("Status update to FAILED skipped by CAS. documentId={}", documentId.value());
                return;
            }
            log.warn("Document processing failed. documentId={}, reason={}", documentId.value(), reason, ex);
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
