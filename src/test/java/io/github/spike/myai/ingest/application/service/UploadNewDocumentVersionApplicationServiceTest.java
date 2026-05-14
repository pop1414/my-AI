package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.UploadNewDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionUploadResult;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.shared.rest.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class UploadNewDocumentVersionApplicationServiceTest {

    @Test
    @DisplayName("最新版本为 INDEXED 且内容变化时，应追加 UPLOADED 新版本")
    void handle_shouldAppendNewVersion_whenLatestIndexedAndContentChanged() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-1", 2, "hash-old", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-1")))).thenReturn(Optional.of(document));
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(new DocumentId("doc-1")))).thenReturn(2);
        when(sourceStorage.saveVersionIfAbsent(eq(new DocumentId("doc-1")), eq(3), eq("new.pdf"), eq(bytes("new content"))))
                .thenReturn(true);
        when(repository.appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-1")), eq(2), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(true);
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(
                        repository,
                        sourceStorage,
                        currentUserProvider,
                        authorizationService,
                        auditEventRepository);

        DocumentVersionUploadResult result = service.handle(
                new UploadNewDocumentVersionCommand("doc-1", " new.pdf ", 42L, "hash-new", 2, bytes("new content")));

        assertTrue(result.versionCreated());
        assertEquals("CREATED", result.versionResultType());
        assertEquals(3, result.versionNumber());
        assertEquals(2, result.previousVersionNumber());
        assertEquals(3, result.latestVersionNumber());
        assertEquals(2, result.askableVersionNumber());
        assertTrue(result.canAskNow());
        assertEquals("UPLOADED", result.status());
        verify(authorizationService).requireCanManageDocument(any(CurrentUser.class), eq("doc-1"), eq("kb-1"));
        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(repository).appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-1")), eq(2), versionCaptor.capture(), any(Instant.class));
        assertEquals(3, versionCaptor.getValue().versionNumber());
        assertEquals("hash-new", versionCaptor.getValue().fileHash());
        assertEquals("new.pdf", versionCaptor.getValue().filename());
        assertEquals(UploadStatus.UPLOADED, versionCaptor.getValue().status());
        assertEquals(DocumentVersionOriginType.UPLOAD, versionCaptor.getValue().versionOriginType());
        assertEquals("user-1", versionCaptor.getValue().createdByUserId());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("DOCUMENT_VERSION_UPLOAD_REQUESTED", auditCaptor.getValue().eventType());
        assertEquals("DOCUMENT_VERSION", auditCaptor.getValue().targetType());
        assertEquals("doc-1:3", auditCaptor.getValue().targetId());
        assertTrue(auditCaptor.getValue().metadata().contains("\"versionResultType\":\"CREATED\""));
        InOrder inOrder = Mockito.inOrder(sourceStorage, repository);
        inOrder.verify(repository).appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-1")), eq(2), any(DocumentVersion.class), any(Instant.class));
        inOrder.verify(sourceStorage).saveVersionIfAbsent(eq(new DocumentId("doc-1")), eq(3), eq("new.pdf"), eq(bytes("new content")));
    }

    @Test
    @DisplayName("新文件与当前最新版本同内容时，应成功复用且不创建新版本")
    void handle_shouldReuseLatestVersion_whenIdenticalContent() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-2", 4, "hash-same", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-2")))).thenReturn(Optional.of(document));
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(new DocumentId("doc-2")))).thenReturn(4);
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        DocumentVersionUploadResult result = service.handle(
                new UploadNewDocumentVersionCommand("doc-2", "same-name.pdf", 42L, "hash-same", 4, bytes("same content")));

        assertFalse(result.versionCreated());
        assertEquals("REUSED_IDENTICAL_CONTENT", result.versionResultType());
        assertEquals(4, result.previousVersionNumber());
        assertEquals(4, result.reusedLatestVersionNumber());
        assertEquals(4, result.latestVersionNumber());
        assertEquals(4, result.askableVersionNumber());
        verify(repository, never()).appendUploadVersion(any(), any(), any(Integer.class), any(), any());
        verify(sourceStorage, never()).saveVersionIfAbsent(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("无管理权限时，应返回上传新版本专用业务错误码")
    void handle_shouldThrowBusinessCode_whenManagePermissionDenied() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-3", 1, "hash-old", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-3")))).thenReturn(Optional.of(document));
        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requireCanManageDocument(any(CurrentUser.class), eq("doc-3"), eq("kb-1"));
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-3", "new.pdf", 42L, "hash-new", 1, bytes("new content"))));

        assertEquals("VERSION_UPLOAD_NO_MANAGE_PERMISSION", ex.code());
        verify(repository, never()).appendUploadVersion(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("当前最新版本状态不允许时，应拒绝上传新版本")
    void handle_shouldThrow_whenLatestStatusNotAllowed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-4", 2, "hash-old", UploadStatus.INGESTING);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-4")))).thenReturn(Optional.of(document));
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-4", "new.pdf", 42L, "hash-new", 2, bytes("new content"))));

        assertEquals("VERSION_UPLOAD_NOT_ALLOWED_STATUS", ex.code());
        verify(repository, never()).appendUploadVersion(any(), any(), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("expectedLatestVersionNumber 过期时，应返回并发冲突错误码")
    void handle_shouldThrow_whenExpectedLatestVersionStale() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-5", 3, "hash-old", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-5")))).thenReturn(Optional.of(document));
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-5", "new.pdf", 42L, "hash-new", 2, bytes("new content"))));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository, never()).appendUploadVersion(any(), any(), any(Integer.class), any(), any());
        verify(sourceStorage, never()).saveVersionIfAbsent(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("CAS 追加失败时，应按并发冲突处理")
    void handle_shouldThrowConflict_whenAppendCasFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-6", 2, "hash-old", UploadStatus.FAILED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-6")))).thenReturn(Optional.of(document));
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(new DocumentId("doc-6")))).thenReturn(1);
        when(sourceStorage.saveVersionIfAbsent(eq(new DocumentId("doc-6")), eq(3), eq("new.pdf"), eq(bytes("new content"))))
                .thenReturn(true);
        when(repository.appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-6")), eq(2), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(false);
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-6", "new.pdf", 42L, "hash-new", 2, bytes("new content"))));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(sourceStorage, never()).saveVersionIfAbsent(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("源文件保存失败时，应抛出异常触发事务回滚")
    void handle_shouldThrowToRollbackTransaction_whenSourceSaveFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-7", 2, "hash-old", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-7")))).thenReturn(Optional.of(document));
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(new DocumentId("doc-7")))).thenReturn(2);
        when(repository.appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-7")), eq(2), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(true);
        doThrow(new IllegalStateException("disk full"))
                .when(sourceStorage)
                .saveVersionIfAbsent(eq(new DocumentId("doc-7")), eq(3), eq("new.pdf"), eq(bytes("new content")));
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        assertThrows(IllegalStateException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-7", "new.pdf", 42L, "hash-new", 2, bytes("new content"))));

        verify(repository).appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-7")), eq(2), any(DocumentVersion.class), any(Instant.class));
    }

    @Test
    @DisplayName("版本源文件已存在但内容不一致时，应按 latest 并发冲突处理")
    void handle_shouldThrowConflict_whenVersionSourceContentConflict() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-8", 2, "hash-old", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-8")))).thenReturn(Optional.of(document));
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(new DocumentId("doc-8")))).thenReturn(2);
        when(repository.appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-8")), eq(2), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(true);
        doThrow(new IllegalStateException(DocumentSourceStorage.VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE))
                .when(sourceStorage)
                .saveVersionIfAbsent(eq(new DocumentId("doc-8")), eq(3), eq("new.pdf"), eq(bytes("new content")));
        UploadNewDocumentVersionApplicationService service =
                new UploadNewDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(
                new UploadNewDocumentVersionCommand("doc-8", "new.pdf", 42L, "hash-new", 2, bytes("new content"))));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository).appendUploadVersion(eq("workspace-a"), eq(new DocumentId("doc-8")), eq(2), any(DocumentVersion.class), any(Instant.class));
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }

    private static Document document(
            String documentId,
            int latestVersionNumber,
            String fileHash,
            UploadStatus status) {
        Instant now = Instant.now();
        return new Document(
                new DocumentId(documentId),
                "workspace-a",
                "kb-1",
                latestVersionNumber,
                DocumentVersionOriginType.UPLOAD,
                fileHash,
                "latest.pdf",
                128L,
                status,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "version-" + latestVersionNumber + "-v1",
                null,
                now,
                now);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
