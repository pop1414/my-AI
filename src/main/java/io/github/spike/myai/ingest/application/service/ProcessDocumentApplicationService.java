package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.usecase.ProcessDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.domain.port.DocumentTextParser;
import io.github.spike.myai.ingest.domain.port.DocumentVectorIndexer;
import java.time.Instant;
import java.util.List;
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

    private final DocumentRepository documentRepository;
    private final DocumentSourceStorage sourceStorage;
    private final DocumentTextParser documentTextParser;
    private final DocumentChunker documentChunker;
    private final DocumentVectorIndexer documentVectorIndexer;

    public ProcessDocumentApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage sourceStorage,
            DocumentTextParser documentTextParser,
            DocumentChunker documentChunker,
            DocumentVectorIndexer documentVectorIndexer) {
        this.documentRepository = documentRepository;
        this.sourceStorage = sourceStorage;
        this.documentTextParser = documentTextParser;
        this.documentChunker = documentChunker;
        this.documentVectorIndexer = documentVectorIndexer;
    }

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
            List<String> chunks = documentChunker.chunk(text);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("no chunks generated");
            }
            // 4) 向量写入（内部保证幂等写入语义）。
            documentVectorIndexer.index(document, chunks);

            // 5) 状态收口：仅当当前仍是 INGESTING 时，才允许推进到 INDEXED。
            // 若 CAS 失败，说明状态已被其它流程改变，此处不再强写。
            boolean updated = documentRepository.compareAndSetStatus(
                    documentId,
                    UploadStatus.INGESTING,
                    UploadStatus.INDEXED,
                    null,
                    Instant.now());
            if (!updated) {
                log.warn("Status update to INDEXED skipped by CAS. documentId={}", documentId.value());
                return;
            }
            log.info("Document processed successfully. documentId={}, chunks={}", documentId.value(), chunks.size());
        } catch (Exception ex) {
            // 失败收口：统一截断失败原因后写入 FAILED，避免错误消息过长污染存储。
            String reason = trimFailureReason(ex.getMessage());
            documentRepository.compareAndSetStatus(
                    documentId,
                    UploadStatus.INGESTING,
                    UploadStatus.FAILED,
                    reason,
                    Instant.now());
            log.warn("Document processing failed. documentId={}, reason={}", documentId.value(), reason, ex);
        }
    }

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
}
