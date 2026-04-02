package io.github.spike.myai.ingest.infrastructure.worker;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.usecase.ClaimNextUploadedDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * InProcessWorker 单元测试。
 */
class InProcessWorkerTest {

    @Test
    @DisplayName("轮询时应调用一次抢占用例")
    void pollAndClaim_shouldInvokeUseCase() {
        ClaimNextUploadedDocumentUseCase useCase = Mockito.mock(ClaimNextUploadedDocumentUseCase.class);
        when(useCase.handle()).thenReturn(Optional.of(new DocumentId("doc-worker-1")));
        InProcessWorker worker = new InProcessWorker(useCase);

        worker.pollAndClaim();

        verify(useCase, times(1)).handle();
    }
}

