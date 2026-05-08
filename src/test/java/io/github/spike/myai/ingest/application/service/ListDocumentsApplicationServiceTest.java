package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentListItem;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentListRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ListDocumentsApplicationServiceTest {

    @Test
    @DisplayName("未指定状态时应默认排除已删除文档")
    void handle_shouldExcludeDeletedByDefault_whenStatusMissing() {
        DocumentListRepository repository = Mockito.mock(DocumentListRepository.class);
        ListDocumentsApplicationService service = new ListDocumentsApplicationService(repository);
        Instant now = Instant.parse("2026-05-07T12:00:00Z");
        when(repository.findPage(Mockito.any())).thenReturn(new DocumentListPage(
                List.of(new DocumentListItem(
                        new DocumentId("doc-1"),
                        "kb-1",
                        "demo.txt",
                        128L,
                        UploadStatus.INDEXED,
                        null,
                        now.minusSeconds(60),
                        now)),
                1L,
                20,
                0));

        DocumentListPageResult result = service.handle(new ListDocumentsQuery("kb-1", null, "demo", 20, 0));

        ArgumentCaptor<io.github.spike.myai.ingest.domain.model.DocumentListFilter> captor =
                ArgumentCaptor.forClass(io.github.spike.myai.ingest.domain.model.DocumentListFilter.class);
        verify(repository).findPage(captor.capture());
        assertEquals("kb-1", captor.getValue().kbId());
        assertEquals("demo", captor.getValue().filename());
        assertNull(captor.getValue().status());
        assertTrue(captor.getValue().excludeDeleted());
        assertEquals("INDEXED", result.items().getFirst().status());
    }

    @Test
    @DisplayName("显式查询 DELETED 时不应附带默认排除条件")
    void handle_shouldKeepDeletedOnly_whenStatusIsDeleted() {
        DocumentListRepository repository = Mockito.mock(DocumentListRepository.class);
        ListDocumentsApplicationService service = new ListDocumentsApplicationService(repository);
        Instant now = Instant.parse("2026-05-07T12:00:00Z");
        when(repository.findPage(Mockito.any())).thenReturn(new DocumentListPage(
                List.of(new DocumentListItem(
                        new DocumentId("doc-deleted"),
                        "kb-2",
                        "old.txt",
                        64L,
                        UploadStatus.DELETED,
                        "should-hide",
                        now.minusSeconds(120),
                        now)),
                1L,
                10,
                5));

        DocumentListPageResult result = service.handle(new ListDocumentsQuery(null, "DELETED", null, 10, 5));

        ArgumentCaptor<io.github.spike.myai.ingest.domain.model.DocumentListFilter> captor =
                ArgumentCaptor.forClass(io.github.spike.myai.ingest.domain.model.DocumentListFilter.class);
        verify(repository).findPage(captor.capture());
        assertEquals(UploadStatus.DELETED, captor.getValue().status());
        assertFalse(captor.getValue().excludeDeleted());
        assertEquals("DELETED", result.items().getFirst().status());
        assertNull(result.items().getFirst().failureReason());
    }
}
