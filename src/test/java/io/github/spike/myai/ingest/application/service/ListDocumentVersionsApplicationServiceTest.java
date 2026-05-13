package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.ListDocumentVersionsQuery;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistory;
import io.github.spike.myai.ingest.domain.model.DocumentVersionHistoryItem;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentVersionHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class ListDocumentVersionsApplicationServiceTest {

    @Test
    @DisplayName("查询版本历史时，应校验读取权限并返回版本号倒序结果")
    void handle_shouldReturnVersionHistory_whenDocumentExists() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        DocumentId documentId = new DocumentId("doc-100");
        when(documentRepository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document(documentId, UploadStatus.INDEXED)));
        when(historyRepository.findByDocumentId(eq("workspace-a"), eq(documentId)))
                .thenReturn(history(documentId, List.of(
                        historyItem(documentId, 3, DocumentVersionOriginType.ROLLBACK, 1, UploadStatus.INDEXED),
                        historyItem(documentId, 2, DocumentVersionOriginType.UPLOAD, null, UploadStatus.INDEXED),
                        historyItem(documentId, 1, DocumentVersionOriginType.UPLOAD, null, UploadStatus.FAILED))));

        DocumentVersionHistoryResult result = service.handle(new ListDocumentVersionsQuery(" doc-100 "));

        assertEquals("doc-100", result.documentId());
        assertEquals("versionNumber,DESC", result.sort());
        assertEquals(3, result.versions().size());
        assertEquals(3, result.versions().get(0).versionNumber());
        assertEquals("ROLLBACK", result.versions().get(0).versionOriginType());
        assertEquals(1, result.versions().get(0).rollbackFromVersionNumber());
        assertTrue(result.versions().get(0).isLatestVersion());
        assertTrue(result.versions().get(0).isAskableVersion());
        assertEquals(2, result.versions().get(1).versionNumber());
        assertFalse(result.versions().get(1).isLatestVersion());
        assertFalse(result.versions().get(1).isAskableVersion());
        assertEquals("parse failed", result.versions().get(2).failureReason());
        verify(authorizationService).requireCanReadDocument(any(CurrentUser.class), eq("doc-100"), eq("kb-1"));
    }

    @Test
    @DisplayName("最新版本未索引时，可问答标记应为 false")
    void handle_shouldMarkAskableFalse_whenLatestVersionIsNotIndexed() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        DocumentId documentId = new DocumentId("doc-200");
        when(documentRepository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document(documentId, UploadStatus.FAILED)));
        when(historyRepository.findByDocumentId(eq("workspace-a"), eq(documentId)))
                .thenReturn(history(
                        documentId,
                        List.of(historyItem(documentId, 3, DocumentVersionOriginType.UPLOAD, null, UploadStatus.FAILED))));

        DocumentVersionHistoryResult result = service.handle(new ListDocumentVersionsQuery("doc-200"));

        assertTrue(result.versions().get(0).isLatestVersion());
        assertFalse(result.versions().get(0).isAskableVersion());
        assertEquals("parse failed", result.versions().get(0).failureReason());
    }

    @Test
    @DisplayName("最新版本不可问答时，应将最近一个已索引历史版本标记为可问答")
    void handle_shouldMarkNewestIndexedHistoryVersionAskable_whenLatestVersionIsNotIndexed() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        DocumentId documentId = new DocumentId("doc-201");
        when(documentRepository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document(documentId, UploadStatus.FAILED)));
        when(historyRepository.findByDocumentId(eq("workspace-a"), eq(documentId)))
                .thenReturn(history(documentId, List.of(
                        historyItem(documentId, 3, DocumentVersionOriginType.UPLOAD, null, UploadStatus.FAILED),
                        historyItem(documentId, 2, DocumentVersionOriginType.UPLOAD, null, UploadStatus.INDEXED),
                        historyItem(documentId, 1, DocumentVersionOriginType.UPLOAD, null, UploadStatus.INDEXED))));

        DocumentVersionHistoryResult result = service.handle(new ListDocumentVersionsQuery("doc-201"));

        assertTrue(result.versions().get(0).isLatestVersion());
        assertFalse(result.versions().get(0).isAskableVersion());
        assertFalse(result.versions().get(1).isLatestVersion());
        assertTrue(result.versions().get(1).isAskableVersion());
        assertFalse(result.versions().get(2).isLatestVersion());
        assertFalse(result.versions().get(2).isAskableVersion());
    }

    @Test
    @DisplayName("文档存在但版本事实为空时，应返回空版本列表")
    void handle_shouldReturnEmptyVersions_whenHistoryIsEmpty() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        DocumentId documentId = new DocumentId("doc-empty");
        when(documentRepository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document(documentId, UploadStatus.INDEXED)));
        when(historyRepository.findByDocumentId(eq("workspace-a"), eq(documentId)))
                .thenReturn(history(documentId, List.of()));

        DocumentVersionHistoryResult result = service.handle(new ListDocumentVersionsQuery("doc-empty"));

        assertEquals(0, result.versions().size());
    }

    @Test
    @DisplayName("读取权限不足时，应抛出 AccessDeniedException 且不查询版本历史")
    void handle_shouldThrowAccessDeniedAndSkipHistoryQuery_whenReadAccessDenied() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        DocumentId documentId = new DocumentId("doc-denied");
        when(documentRepository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document(documentId, UploadStatus.INDEXED)));
        Mockito.doThrow(new AccessDeniedException("document read access denied"))
                .when(authorizationService)
                .requireCanReadDocument(any(CurrentUser.class), eq("doc-denied"), eq("kb-1"));

        assertThrows(
                AccessDeniedException.class,
                () -> service.handle(new ListDocumentVersionsQuery("doc-denied")));

        verify(historyRepository, never())
                .findByDocumentId(any(String.class), any(DocumentId.class));
    }

    @Test
    @DisplayName("文档不存在时，应抛出 DocumentNotFoundException")
    void handle_shouldThrowNotFound_whenDocumentMissing() {
        DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        DocumentVersionHistoryRepository historyRepository = Mockito.mock(DocumentVersionHistoryRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ListDocumentVersionsApplicationService service = new ListDocumentVersionsApplicationService(
                documentRepository,
                historyRepository,
                currentUserProvider,
                authorizationService);
        when(documentRepository.findById(eq("workspace-a"), eq(new DocumentId("doc-missing"))))
                .thenReturn(Optional.empty());

        assertThrows(
                DocumentNotFoundException.class,
                () -> service.handle(new ListDocumentVersionsQuery("doc-missing")));
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }

    private static Document document(DocumentId documentId, UploadStatus status) {
        return new Document(
                documentId,
                "workspace-a",
                "kb-1",
                3,
                DocumentVersionOriginType.UPLOAD,
                "hash-" + documentId.value(),
                "latest.pdf",
                300,
                status,
                status == UploadStatus.FAILED ? "parse failed" : null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                null,
                Instant.parse("2026-05-08T10:00:00Z"),
                Instant.parse("2026-05-08T10:05:00Z"));
    }

    private static DocumentVersionHistoryItem historyItem(
            DocumentId documentId,
            int versionNumber,
            DocumentVersionOriginType originType,
            Integer rollbackFromVersionNumber,
            UploadStatus status) {
        return new DocumentVersionHistoryItem(
                documentId,
                "workspace-a",
                "kb-1",
                3,
                versionNumber,
                originType,
                rollbackFromVersionNumber,
                "version-" + versionNumber + ".pdf",
                versionNumber * 100L,
                status,
                status == UploadStatus.FAILED ? "parse failed" : null,
                Instant.parse("2026-05-08T0" + versionNumber + ":00:00Z"),
                Instant.parse("2026-05-08T0" + versionNumber + ":05:00Z"));
    }

    private static DocumentVersionHistory history(DocumentId documentId, List<DocumentVersionHistoryItem> items) {
        return new DocumentVersionHistory(documentId, items);
    }
}
