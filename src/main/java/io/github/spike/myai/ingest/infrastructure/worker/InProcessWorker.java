package io.github.spike.myai.ingest.infrastructure.worker;

import io.github.spike.myai.ingest.application.usecase.ClaimNextUploadedDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单进程异步 Worker（V1 骨架）。
 *
 * <p>说明：
 * <ul>
 *     <li>默认关闭（myai.ingest.worker.enabled=false）。</li>
 *     <li>当前阶段仅执行“候选挑选 + CAS 抢占”。</li>
 *     <li>真正处理链路（解析/分块/向量化）将在下一阶段接入。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "myai.ingest.worker", name = "enabled", havingValue = "true")
public class InProcessWorker {

    private static final Logger log = LoggerFactory.getLogger(InProcessWorker.class);

    private final ClaimNextUploadedDocumentUseCase claimNextUploadedDocumentUseCase;

    public InProcessWorker(ClaimNextUploadedDocumentUseCase claimNextUploadedDocumentUseCase) {
        this.claimNextUploadedDocumentUseCase = claimNextUploadedDocumentUseCase;
    }

    /**
     * 周期轮询并尝试抢占一条待处理文档。
     */
    @Scheduled(fixedDelayString = "${myai.ingest.worker.poll-delay-ms:5000}")
    public void pollAndClaim() {
        Optional<DocumentId> claimedDocumentId = claimNextUploadedDocumentUseCase.handle();
        if (claimedDocumentId.isPresent()) {
            log.info(
                    "InProcessWorker claimed document for processing pipeline. documentId={}",
                    claimedDocumentId.get().value());
        }
    }
}

