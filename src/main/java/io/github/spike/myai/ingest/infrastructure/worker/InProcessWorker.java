package io.github.spike.myai.ingest.infrastructure.worker;

import io.github.spike.myai.ingest.application.usecase.ClaimNextUploadedDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.ProcessDocumentUseCase;
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
 *     <li>执行“候选挑选 + CAS 抢占 + 处理用例调用”。</li>
 *     <li>处理失败将由处理用例推进到 FAILED 状态。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "myai.ingest.worker", name = "enabled", havingValue = "true")
public class InProcessWorker {

    private static final Logger log = LoggerFactory.getLogger(InProcessWorker.class);

    private final ClaimNextUploadedDocumentUseCase claimNextUploadedDocumentUseCase;
    private final ProcessDocumentUseCase processDocumentUseCase;

    public InProcessWorker(
            ClaimNextUploadedDocumentUseCase claimNextUploadedDocumentUseCase,
            ProcessDocumentUseCase processDocumentUseCase) {
        this.claimNextUploadedDocumentUseCase = claimNextUploadedDocumentUseCase;
        this.processDocumentUseCase = processDocumentUseCase;
    }

    /**
     * 周期轮询并尝试抢占一条待处理文档。
     */
    @Scheduled(fixedDelayString = "${myai.ingest.worker.poll-delay-ms:5000}")
    public void pollAndClaim() {
        // 每轮只处理一个 documentId，避免单轮任务过重影响系统抖动。
        Optional<DocumentId> claimedDocumentId = claimNextUploadedDocumentUseCase.handle();
        if (claimedDocumentId.isPresent()) {
            log.info(
                    "InProcessWorker claimed document for processing pipeline. documentId={}",
                    claimedDocumentId.get().value());
            // 抢占成功后立即进入处理链路；处理结果由用例内部推进状态。
            processDocumentUseCase.handle(claimedDocumentId.get());
        }
    }
}
