package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.Optional;

/**
 * 抢占下一条待处理文档资产用例。
 *
 * <p>职责：
 * <ul>
 *     <li>从 UPLADED 队列中选择一条候选文档。</li>
 *     <li>通过 CAS 将状态推进为 INGESTING。</li>
 * </ul>
 */
public interface ClaimNextUploadedDocumentUseCase {

    /**
     * 抢占并推进下一条待处理文档。
     *
     * @return 抢占成功时返回文档资产 ID，否则返回空
     */
    Optional<DocumentId> handle();
}

