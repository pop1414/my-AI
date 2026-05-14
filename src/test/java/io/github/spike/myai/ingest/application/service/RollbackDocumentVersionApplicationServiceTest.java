package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.github.spike.myai.ingest.application.command.RollbackDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionRollbackResult;
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

class RollbackDocumentVersionApplicationServiceTest {

    @Test
    @DisplayName("目标历史版本已 INDEXED 时，应创建 ROLLBACK 来源的 UPLOADED 新最新版本")
    void handle_shouldAppendRollbackVersion_whenTargetIndexed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        DocumentId documentId = new DocumentId("doc-1");
        Document latest = document("doc-1", 3, "hash-latest", UploadStatus.INDEXED);
        DocumentVersion targetVersion = version(documentId, 1, "hash-v1", "v1.pdf", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(documentId))).thenReturn(Optional.of(latest));
        when(repository.findVersionByNumber(eq("workspace-a"), eq(documentId), eq(1)))
                .thenReturn(Optional.of(targetVersion));
        when(sourceStorage.loadVersion(eq(documentId), eq(1), eq("v1.pdf")))
                .thenReturn(Optional.of(bytes("v1 content")));
        when(sourceStorage.saveVersionIfAbsent(eq(documentId), eq(4), eq("v1.pdf"), eq(bytes("v1 content"))))
                .thenReturn(true);
        when(repository.appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(true);
        when(repository.findLatestIndexedVersionNumber(eq("workspace-a"), eq(documentId))).thenReturn(1);
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(
                        repository,
                        sourceStorage,
                        currentUserProvider,
                        authorizationService,
                        auditEventRepository);

        DocumentVersionRollbackResult result =
                service.handle(new RollbackDocumentVersionCommand("doc-1", 1, 3));

        assertEquals("doc-1", result.documentId());
        assertEquals(4, result.versionNumber());
        assertEquals(1, result.rollbackFromVersionNumber());
        assertEquals(4, result.latestVersionNumber());
        assertEquals(1, result.askableVersionNumber());
        assertTrue(result.canAskNow());
        assertEquals("UPLOADED", result.status());
        assertEquals("ROLLBACK", result.versionOriginType());
        verify(authorizationService).requireCanManageDocument(any(CurrentUser.class), eq("doc-1"), eq("kb-1"));
        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(repository).appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), versionCaptor.capture(), any(Instant.class));
        DocumentVersion rollbackVersion = versionCaptor.getValue();
        assertEquals(4, rollbackVersion.versionNumber());
        assertEquals(DocumentVersionOriginType.ROLLBACK, rollbackVersion.versionOriginType());
        assertEquals(1, rollbackVersion.rollbackFromVersionNumber());
        assertEquals("hash-v1", rollbackVersion.fileHash());
        assertEquals("v1.pdf", rollbackVersion.filename());
        assertEquals(UploadStatus.UPLOADED, rollbackVersion.status());
        assertEquals("user-1", rollbackVersion.createdByUserId());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("DOCUMENT_VERSION_ROLLBACK_REQUESTED", auditCaptor.getValue().eventType());
        assertEquals("DOCUMENT_VERSION", auditCaptor.getValue().targetType());
        assertEquals("doc-1:4", auditCaptor.getValue().targetId());
        assertTrue(auditCaptor.getValue().metadata().contains("\"versionResultType\":\"CREATED\""));
        InOrder inOrder = Mockito.inOrder(sourceStorage, repository);
        inOrder.verify(repository).appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), any(DocumentVersion.class), any(Instant.class));
        inOrder.verify(sourceStorage).saveVersionIfAbsent(eq(documentId), eq(4), eq("v1.pdf"), eq(bytes("v1 content")));
    }

    @Test
    @DisplayName("无管理权限时，应返回版本回退专用业务错误码")
    void handle_shouldThrowBusinessCode_whenManagePermissionDenied() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        Document document = document("doc-2", 2, "hash-latest", UploadStatus.INDEXED);
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-2")))).thenReturn(Optional.of(document));
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requireCanManageDocument(any(CurrentUser.class), eq("doc-2"), eq("kb-1"));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-2", 1, 2)));

        assertEquals("VERSION_ROLLBACK_NO_MANAGE_PERMISSION", ex.code());
        verify(repository, never()).appendRollbackVersion(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("expectedLatestVersionNumber 过期时，应返回并发冲突错误码")
    void handle_shouldThrow_whenExpectedLatestVersionStale() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-3"))))
                .thenReturn(Optional.of(document("doc-3", 3, "hash-latest", UploadStatus.INDEXED)));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-3", 1, 2)));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository, never()).findVersionByNumber(any(), any(), anyInt());
    }

    @Test
    @DisplayName("当前最新版本状态不允许时，应拒绝版本回退")
    void handle_shouldThrow_whenLatestStatusNotAllowed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-4"))))
                .thenReturn(Optional.of(document("doc-4", 3, "hash-latest", UploadStatus.INGESTING)));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-4", 1, 3)));

        assertEquals("VERSION_ROLLBACK_NOT_ALLOWED_STATUS", ex.code());
        verify(repository, never()).appendRollbackVersion(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("当前最新版本不能作为回退目标")
    void handle_shouldThrow_whenTargetIsLatest() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        when(repository.findById(eq("workspace-a"), eq(new DocumentId("doc-5"))))
                .thenReturn(Optional.of(document("doc-5", 3, "hash-latest", UploadStatus.INDEXED)));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-5", 3, 3)));

        assertEquals("VERSION_ROLLBACK_TARGET_IS_LATEST", ex.code());
        verify(repository, never()).findVersionByNumber(any(), any(), anyInt());
    }

    @Test
    @DisplayName("目标历史版本未 INDEXED 时，应拒绝版本回退")
    void handle_shouldThrow_whenTargetNotIndexed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        DocumentId documentId = new DocumentId("doc-6");
        when(repository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document("doc-6", 3, "hash-latest", UploadStatus.INDEXED)));
        when(repository.findVersionByNumber(eq("workspace-a"), eq(documentId), eq(1)))
                .thenReturn(Optional.of(version(documentId, 1, "hash-v1", "v1.pdf", UploadStatus.FAILED)));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-6", 1, 3)));

        assertEquals("VERSION_ROLLBACK_TARGET_NOT_INDEXED", ex.code());
        verify(sourceStorage, never()).loadVersion(any(), anyInt(), any());
        verify(repository, never()).appendRollbackVersion(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("CAS 追加失败时，应按并发冲突处理")
    void handle_shouldThrowConflict_whenAppendCasFailed() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        DocumentId documentId = new DocumentId("doc-7");
        when(repository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document("doc-7", 3, "hash-latest", UploadStatus.FAILED)));
        when(repository.findVersionByNumber(eq("workspace-a"), eq(documentId), eq(1)))
                .thenReturn(Optional.of(version(documentId, 1, "hash-v1", "v1.pdf", UploadStatus.INDEXED)));
        when(sourceStorage.loadVersion(eq(documentId), eq(1), eq("v1.pdf")))
                .thenReturn(Optional.of(bytes("v1 content")));
        when(sourceStorage.saveVersionIfAbsent(eq(documentId), eq(4), eq("v1.pdf"), eq(bytes("v1 content"))))
                .thenReturn(true);
        when(repository.appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(false);
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-7", 1, 3)));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(sourceStorage, never()).saveVersionIfAbsent(any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("目标版本源文件缺失时，不应追加数据库版本")
    void handle_shouldNotAppendVersion_whenTargetSourceMissing() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        DocumentId documentId = new DocumentId("doc-8");
        when(repository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document("doc-8", 3, "hash-latest", UploadStatus.INDEXED)));
        when(repository.findVersionByNumber(eq("workspace-a"), eq(documentId), eq(1)))
                .thenReturn(Optional.of(version(documentId, 1, "hash-v1", "v1.pdf", UploadStatus.INDEXED)));
        when(sourceStorage.loadVersion(eq(documentId), eq(1), eq("v1.pdf"))).thenReturn(Optional.empty());
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-8", 1, 3)));

        assertEquals("VERSION_ROLLBACK_SOURCE_FILE_MISSING", ex.code());
        verify(repository, never()).appendRollbackVersion(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("版本源文件已存在但内容不一致时，应按 latest 并发冲突处理")
    void handle_shouldThrowConflict_whenVersionSourceContentConflict() {
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        DocumentSourceStorage sourceStorage = Mockito.mock(DocumentSourceStorage.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        DocumentId documentId = new DocumentId("doc-9");
        when(repository.findById(eq("workspace-a"), eq(documentId)))
                .thenReturn(Optional.of(document("doc-9", 3, "hash-latest", UploadStatus.INDEXED)));
        when(repository.findVersionByNumber(eq("workspace-a"), eq(documentId), eq(1)))
                .thenReturn(Optional.of(version(documentId, 1, "hash-v1", "v1.pdf", UploadStatus.INDEXED)));
        when(sourceStorage.loadVersion(eq(documentId), eq(1), eq("v1.pdf")))
                .thenReturn(Optional.of(bytes("v1 content")));
        when(repository.appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), any(DocumentVersion.class), any(Instant.class)))
                .thenReturn(true);
        doThrow(new IllegalStateException(DocumentSourceStorage.VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE))
                .when(sourceStorage)
                .saveVersionIfAbsent(eq(documentId), eq(4), eq("v1.pdf"), eq(bytes("v1 content")));
        RollbackDocumentVersionApplicationService service =
                new RollbackDocumentVersionApplicationService(repository, sourceStorage, currentUserProvider, authorizationService);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.handle(new RollbackDocumentVersionCommand("doc-9", 1, 3)));

        assertEquals("VERSION_CONFLICT_STALE_LATEST_VERSION", ex.code());
        verify(repository).appendRollbackVersion(eq("workspace-a"), eq(documentId), eq(3), any(DocumentVersion.class), any(Instant.class));
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

    private static DocumentVersion version(
            DocumentId documentId,
            int versionNumber,
            String fileHash,
            String filename,
            UploadStatus status) {
        Instant now = Instant.now();
        return new DocumentVersion(
                documentId,
                versionNumber,
                DocumentVersionOriginType.UPLOAD,
                null,
                fileHash,
                filename,
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
                "version-" + versionNumber + "-v1",
                null,
                now,
                now);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
