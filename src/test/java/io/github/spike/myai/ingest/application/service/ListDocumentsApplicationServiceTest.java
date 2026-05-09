package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
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
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentsApplicationService service =
                new ListDocumentsApplicationService(repository, currentUserProvider, authorizationService);
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
        assertEquals("workspace-a", captor.getValue().workspaceId());
        assertEquals("INDEXED", result.items().getFirst().status());
        verify(authorizationService).requireCanReadDocument(any(CurrentUser.class), eq("doc-1"), eq("kb-1"));
    }

    @Test
    @DisplayName("显式查询 DELETED 时不应附带默认排除条件")
    void handle_shouldKeepDeletedOnly_whenStatusIsDeleted() {
        DocumentListRepository repository = Mockito.mock(DocumentListRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentsApplicationService service =
                new ListDocumentsApplicationService(repository, currentUserProvider, authorizationService);
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
                0));

        DocumentListPageResult result = service.handle(new ListDocumentsQuery(null, "DELETED", null, 10, 0));

        ArgumentCaptor<io.github.spike.myai.ingest.domain.model.DocumentListFilter> captor =
                ArgumentCaptor.forClass(io.github.spike.myai.ingest.domain.model.DocumentListFilter.class);
        verify(repository).findPage(captor.capture());
        assertEquals(UploadStatus.DELETED, captor.getValue().status());
        assertFalse(captor.getValue().excludeDeleted());
        assertEquals("DELETED", result.items().getFirst().status());
        assertNull(result.items().getFirst().failureReason());
    }

    @Test
    @DisplayName("无文档读取权限的结果项应在列表中被过滤")
    void handle_shouldFilterUnauthorizedItems() {
        DocumentListRepository repository = Mockito.mock(DocumentListRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentsApplicationService service =
                new ListDocumentsApplicationService(repository, currentUserProvider, authorizationService);
        Instant now = Instant.parse("2026-05-07T12:00:00Z");
        when(repository.findPage(Mockito.any())).thenReturn(new DocumentListPage(
                List.of(
                        new DocumentListItem(
                                new DocumentId("doc-1"),
                                "workspace-a",
                                "kb-1",
                                "allow.txt",
                                128L,
                                UploadStatus.INDEXED,
                                null,
                                now.minusSeconds(60),
                                now),
                        new DocumentListItem(
                                new DocumentId("doc-2"),
                                "workspace-a",
                                "kb-1",
                                "deny.txt",
                                128L,
                                UploadStatus.INDEXED,
                                null,
                                now.minusSeconds(30),
                                now)),
                2L,
                20,
                0));
        Mockito.doThrow(new org.springframework.security.access.AccessDeniedException("document read access denied"))
                .when(authorizationService)
                .requireCanReadDocument(any(CurrentUser.class), eq("doc-2"), eq("kb-1"));

        DocumentListPageResult result = service.handle(new ListDocumentsQuery("kb-1", null, null, 20, 0));

        assertEquals(1, result.items().size());
        assertEquals(1L, result.total());
        assertEquals("doc-1", result.items().getFirst().documentId());
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
