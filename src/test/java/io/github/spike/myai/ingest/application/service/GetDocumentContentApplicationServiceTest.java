package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.ingest.application.query.DocumentContentSource;
import io.github.spike.myai.ingest.application.query.GetDocumentContentQuery;
import io.github.spike.myai.ingest.application.result.DocumentContentResult;
import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.github.spike.myai.shared.rest.BusinessException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/**
 * GetDocumentContentApplicationService 的应用层单元测试。
 */
class GetDocumentContentApplicationServiceTest {

    @Test
    @DisplayName("latest 为 INDEXED 且 cleaned.md 存在时，应返回 LATEST 正文")
    void handle_shouldReturnLatestContent_whenIndexedArtifactExists() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-content-1");
        Document document = document(documentId, 2, UploadStatus.INDEXED);
        DocumentVersion version = version(documentId, 2, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 2)).thenReturn(Optional.of(version));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(2);
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                2,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.of(new DocumentVersionArtifactContent("key", "# 正文", 9L)));

        DocumentContentResult result = fixture.service.handle(
                new GetDocumentContentQuery("doc-content-1", DocumentContentSource.LATEST));

        assertEquals("doc-content-1", result.documentId());
        assertEquals(2, result.versionNumber());
        assertEquals(2, result.latestVersionNumber());
        assertTrue(result.isLatestVersion());
        assertTrue(result.isAskableVersion());
        assertEquals("LATEST", result.source());
        assertEquals("INDEXED", result.status());
        assertEquals("demo-v2.md", result.filename());
        assertEquals("# 正文", result.contentMarkdown());
        assertEquals(9L, result.contentLength());
        assertFalse(result.truncated());
        verify(fixture.authorizationService).requireCanReadDocument(any(CurrentUser.class), eq("doc-content-1"), eq("kb-1"));
    }

    @Test
    @DisplayName("latest 为 INGESTING 且正文尚未生成时，应返回 CONTENT_NOT_READY")
    void handle_shouldThrowContentNotReady_whenIngestingArtifactMissing() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-ingesting");
        Document document = document(documentId, 3, UploadStatus.INGESTING);
        DocumentVersion version = version(documentId, 3, UploadStatus.INGESTING);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 3)).thenReturn(Optional.of(version));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                3,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(new GetDocumentContentQuery("doc-ingesting", DocumentContentSource.LATEST)));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("CONTENT_NOT_READY", ex.code());
    }

    @Test
    @DisplayName("latest 为 FAILED 且 cleaned.md 存在时，应返回失败版本正文")
    void handle_shouldReturnFailedLatestContent_whenFailedArtifactExists() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-failed");
        Document document = document(documentId, 4, UploadStatus.FAILED);
        DocumentVersion version = version(documentId, 4, UploadStatus.FAILED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 4)).thenReturn(Optional.of(version));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(2);
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                4,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.of(new DocumentVersionArtifactContent("key", "失败版本正文", 15L)));

        DocumentContentResult result = fixture.service.handle(
                new GetDocumentContentQuery("doc-failed", DocumentContentSource.LATEST));

        assertEquals(4, result.versionNumber());
        assertEquals("FAILED", result.status());
        assertFalse(result.isAskableVersion());
        assertEquals("失败版本正文", result.contentMarkdown());
    }

    @Test
    @DisplayName("DELETED document 应拒绝正文读取且不读取 artifact")
    void handle_shouldRejectDeletedDocument() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-deleted");
        when(fixture.documentRepository.findById("workspace-a", documentId))
                .thenReturn(Optional.of(document(documentId, 1, UploadStatus.DELETED)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(new GetDocumentContentQuery("doc-deleted", DocumentContentSource.LATEST)));

        assertEquals(HttpStatus.NOT_FOUND, ex.status());
        assertEquals("DOCUMENT_NOT_FOUND", ex.code());
        verify(fixture.artifactStorage, never()).loadVersionArtifact(any(), any(), eq(1), any(), eq(1024L));
    }

    @Test
    @DisplayName("终态版本正文产物缺失时，应返回 CONTENT_ARTIFACT_MISSING")
    void handle_shouldThrowArtifactMissing_whenTerminalArtifactMissing() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-missing-artifact");
        Document document = document(documentId, 2, UploadStatus.INDEXED);
        DocumentVersion version = version(documentId, 2, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 2)).thenReturn(Optional.of(version));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                2,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(
                        new GetDocumentContentQuery("doc-missing-artifact", DocumentContentSource.LATEST)));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.status());
        assertEquals("CONTENT_ARTIFACT_MISSING", ex.code());
    }

    @Test
    @DisplayName("正文超过读取上限时，应返回 CONTENT_TOO_LARGE")
    void handle_shouldThrowTooLarge_whenArtifactExceedsLimit() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-large");
        Document document = document(documentId, 2, UploadStatus.INDEXED);
        DocumentVersion version = version(documentId, 2, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 2)).thenReturn(Optional.of(version));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                2,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenThrow(new DocumentVersionArtifactTooLargeException(2048L, 1024L));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(new GetDocumentContentQuery("doc-large", DocumentContentSource.LATEST)));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.status());
        assertEquals("CONTENT_TOO_LARGE", ex.code());
    }

    @Test
    @DisplayName("当前用户无文档读取权限时，应返回 DOCUMENT_CONTENT_FORBIDDEN")
    void handle_shouldThrowForbidden_whenReadPermissionDenied() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-denied");
        Document document = document(documentId, 2, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(fixture.authorizationService)
                .requireCanReadDocument(any(CurrentUser.class), eq("doc-denied"), eq("kb-1"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(new GetDocumentContentQuery("doc-denied", DocumentContentSource.LATEST)));

        assertEquals(HttpStatus.FORBIDDEN, ex.status());
        assertEquals("DOCUMENT_CONTENT_FORBIDDEN", ex.code());
        verify(fixture.artifactStorage, never()).loadVersionArtifact(any(), any(), eq(2), any(), eq(1024L));
    }

    @Test
    @DisplayName("askable baseline 与 latest 同为 INDEXED 时，应返回同一版本但 source 为 ASKABLE_BASELINE")
    void handle_shouldReturnAskableBaselineContent_whenLatestIndexed() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-askable-latest");
        Document document = document(documentId, 5, UploadStatus.INDEXED);
        DocumentVersion version = version(documentId, 5, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(5);
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 5)).thenReturn(Optional.of(version));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                5,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.of(new DocumentVersionArtifactContent("key", "askable latest", 15L)));

        DocumentContentResult result = fixture.service.handle(
                new GetDocumentContentQuery("doc-askable-latest", DocumentContentSource.ASKABLE_BASELINE));

        assertEquals(5, result.versionNumber());
        assertEquals(5, result.latestVersionNumber());
        assertTrue(result.isLatestVersion());
        assertTrue(result.isAskableVersion());
        assertEquals("ASKABLE_BASELINE", result.source());
        assertEquals("askable latest", result.contentMarkdown());
    }

    @Test
    @DisplayName("latest 为 INGESTING 且旧版本已 INDEXED 时，askable baseline 应返回旧版本")
    void handle_shouldReturnPreviousIndexedVersion_whenAskableBaselineFallsBackFromIngestingLatest() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-askable-ingesting");
        Document document = document(documentId, 6, UploadStatus.INGESTING);
        DocumentVersion askableVersion = version(documentId, 4, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(4);
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 4))
                .thenReturn(Optional.of(askableVersion));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                4,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.of(new DocumentVersionArtifactContent("key", "old indexed", 11L)));

        DocumentContentResult result = fixture.service.handle(
                new GetDocumentContentQuery("doc-askable-ingesting", DocumentContentSource.ASKABLE_BASELINE));

        assertEquals(4, result.versionNumber());
        assertEquals(6, result.latestVersionNumber());
        assertFalse(result.isLatestVersion());
        assertTrue(result.isAskableVersion());
        assertEquals("ASKABLE_BASELINE", result.source());
        assertEquals("old indexed", result.contentMarkdown());
        verify(fixture.documentRepository).findById("workspace-a", documentId);
        verify(fixture.documentRepository).findLatestIndexedVersionNumber("workspace-a", documentId);
        verify(fixture.documentRepository).findVersionByNumber("workspace-a", documentId, 4);
        verify(fixture.documentRepository, never()).findVersionByNumber("workspace-a", documentId, 6);
        verifyNoMoreInteractions(fixture.documentRepository);
    }

    @Test
    @DisplayName("latest 为 FAILED 且有 cleaned.md 时，askable baseline 仍应返回最近 indexed 版本")
    void handle_shouldReturnPreviousIndexedVersion_whenLatestFailedHasContent() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-askable-failed");
        Document document = document(documentId, 6, UploadStatus.FAILED);
        DocumentVersion askableVersion = version(documentId, 3, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(3);
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 3))
                .thenReturn(Optional.of(askableVersion));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                3,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.of(new DocumentVersionArtifactContent("key", "stable baseline", 15L)));

        DocumentContentResult result = fixture.service.handle(
                new GetDocumentContentQuery("doc-askable-failed", DocumentContentSource.ASKABLE_BASELINE));

        assertEquals(3, result.versionNumber());
        assertEquals(6, result.latestVersionNumber());
        assertFalse(result.isLatestVersion());
        assertTrue(result.isAskableVersion());
        assertEquals("stable baseline", result.contentMarkdown());
    }

    @Test
    @DisplayName("没有可问答版本时，askable baseline 应返回 CONTENT_NOT_READY 且不读取 artifact")
    void handle_shouldThrowContentNotReady_whenAskableBaselineMissing() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-no-askable");
        Document document = document(documentId, 2, UploadStatus.INGESTING);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(0);

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(
                        new GetDocumentContentQuery("doc-no-askable", DocumentContentSource.ASKABLE_BASELINE)));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("CONTENT_NOT_READY", ex.code());
        verify(fixture.artifactStorage, never()).loadVersionArtifact(any(), any(), anyInt(), any(), eq(1024L));
    }

    @Test
    @DisplayName("askable baseline 正文尚未生成时，应返回 CONTENT_NOT_READY")
    void handle_shouldThrowContentNotReady_whenAskableBaselineArtifactMissing() {
        Fixture fixture = new Fixture();
        DocumentId documentId = new DocumentId("doc-askable-missing-artifact");
        Document document = document(documentId, 3, UploadStatus.INDEXED);
        DocumentVersion askableVersion = version(documentId, 3, UploadStatus.INDEXED);
        when(fixture.documentRepository.findById("workspace-a", documentId)).thenReturn(Optional.of(document));
        when(fixture.documentRepository.findLatestIndexedVersionNumber("workspace-a", documentId)).thenReturn(3);
        when(fixture.documentRepository.findVersionByNumber("workspace-a", documentId, 3))
                .thenReturn(Optional.of(askableVersion));
        when(fixture.artifactStorage.loadVersionArtifact(
                "workspace-a",
                documentId,
                3,
                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                1024L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> fixture.service.handle(new GetDocumentContentQuery(
                        "doc-askable-missing-artifact",
                        DocumentContentSource.ASKABLE_BASELINE)));

        assertEquals(HttpStatus.CONFLICT, ex.status());
        assertEquals("CONTENT_NOT_READY", ex.code());
    }

    private static Document document(DocumentId documentId, int latestVersionNumber, UploadStatus status) {
        Instant now = Instant.parse("2026-05-15T08:00:00Z");
        return new Document(
                documentId,
                "workspace-a",
                "kb-1",
                latestVersionNumber,
                DocumentVersionOriginType.UPLOAD,
                "hash-" + latestVersionNumber,
                "demo-v" + latestVersionNumber + ".md",
                128,
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
                "version-" + latestVersionNumber + "-v1",
                null,
                now,
                now);
    }

    private static DocumentVersion version(DocumentId documentId, int versionNumber, UploadStatus status) {
        Instant createdAt = Instant.parse("2026-05-15T08:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-15T08:05:00Z");
        return new DocumentVersion(
                documentId,
                versionNumber,
                DocumentVersionOriginType.UPLOAD,
                null,
                "hash-" + versionNumber,
                "demo-v" + versionNumber + ".md",
                128,
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
                "version-" + versionNumber + "-v1",
                null,
                "user-1",
                createdAt,
                updatedAt);
    }

    private static class Fixture {
        private final DocumentRepository documentRepository = Mockito.mock(DocumentRepository.class);
        private final DocumentProcessingArtifactStorage artifactStorage =
                Mockito.mock(DocumentProcessingArtifactStorage.class);
        private final CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        private final AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        private final GetDocumentContentApplicationService service;

        private Fixture() {
            when(currentUserProvider.requireCurrentUser()).thenReturn(
                    new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
            IngestProperties ingestProperties = new IngestProperties();
            ingestProperties.getStorage().getArtifacts().setMaxReadBytes(1024L);
            this.service = new GetDocumentContentApplicationService(
                    documentRepository,
                    artifactStorage,
                    currentUserProvider,
                    authorizationService,
                    ingestProperties);
        }
    }
}
