package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.usecase.ClaimNextUploadedDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 抢占待处理文档应用服务（Application Service）。
 *
 * <p>该服务实现 {@link ClaimNextUploadedDocumentUseCase} 用例接口，
 * 只负责"挑选 + CAS 抢占"，不执行解析/分块/向量化等后续处理。
 *
 * <h3>核心流程</h3>
 * <ol>
 *   <li>从仓储中查找最早可处理的 UPLOADED 状态文档；</li>
 *   <li>通过 CAS（Compare And Set）将状态原子推进到 INGESTING，
 *       防止多 Worker 并发抢占同一文档；</li>
 *   <li>抢占成功则返回文档 ID，失败则返回空。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class ClaimNextUploadedDocumentApplicationService implements ClaimNextUploadedDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ClaimNextUploadedDocumentApplicationService.class);

    /** 文档仓储端口：用于查找待处理文档与 CAS 状态推进 */
    private final DocumentRepository documentRepository;

    /**
     * 构造器注入。
     *
     * @param documentRepository 文档仓储
     */
    public ClaimNextUploadedDocumentApplicationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public Optional<DocumentId> handle() {
    /**
     * 抢占最早可处理的 UPLOADED 文档。
     *
     * <p>执行流程：
     * <ol>
     *   <li>查询最早可处理记录（按 next_retry_at/created_at 升序）；</li>
     *   <li>无候选时直接返回空；</li>
     *   <li>CAS 抢占：只有当前状态仍为 UPLOADED 才能推进到 INGESTING；</li>
     *   <li>抢占失败（并发冲突）时返回空，由调度器下次重试。</li>
     * </ol>
     *
     * @return 抢占成功的文档 ID，无可用文档时返回 {@link Optional#empty()}
     */
        // 始终取最早可处理的 UPLOADED，尽量保持处理顺序稳定。
        String workspaceId = WorkspaceConstants.DEFAULT_WORKSPACE_ID;
        Optional<Document> candidate = documentRepository.findOldestReadyForProcessing(workspaceId, Instant.now());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        Document document = candidate.get();
        // CAS 抢占：只有当前状态仍为 UPLOADED 才能推进到 INGESTING。
        // 若并发下被其它 worker 先一步更新，本次抢占会返回 false。
        boolean claimed = documentRepository.compareAndSetStatus(
                workspaceId,
                document.documentId(),
                UploadStatus.UPLOADED,
                UploadStatus.INGESTING,
                null,
                Instant.now());
        if (!claimed) {
            log.debug("Skip claim because status changed concurrently. documentId={}", document.documentId().value());
            return Optional.empty();
        }
        log.info("Claimed uploaded document. documentId={}", document.documentId().value());
        return Optional.of(document.documentId());
    }
}
