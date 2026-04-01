package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * GetDocumentStatusApplicationService 的应用层单元测试。
 */
class GetDocumentStatusApplicationServiceTest {

    @Test
    @DisplayName("查询命中时，应返回文档状态结果")
    void handle_shouldReturnResult_whenFound() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        GetDocumentStatusApplicationService service = new GetDocumentStatusApplicationService(repository);
        DocumentId documentId = new DocumentId("doc-100");
        Document document = new Document(
                documentId,
                "kb-1",
                "a.txt",
                128,
                UploadStatus.INDEXED,
                null,
                Instant.now(),
                Instant.now());
        when(repository.findById(documentId)).thenReturn(Optional.of(document));

        DocumentStatusResult result = service.handle(new GetDocumentStatusQuery("doc-100"));

        assertEquals("doc-100", result.documentId().value());
        assertEquals(UploadStatus.INDEXED, result.status());
        verify(repository, times(1)).findById(documentId);
    }

    @Test
    @DisplayName("查询未命中时，应抛出 DocumentNotFoundException")
    void handle_shouldThrowException_whenMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        GetDocumentStatusApplicationService service = new GetDocumentStatusApplicationService(repository);
        when(repository.findById(new DocumentId("doc-missing"))).thenReturn(Optional.empty());

        assertThrows(
                DocumentNotFoundException.class,
                () -> service.handle(new GetDocumentStatusQuery("doc-missing")));
    }
}
