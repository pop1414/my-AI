package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentIdGenerator;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

/**
 * AcceptUploadApplicationService 的应用层单元测试。
 *
 * <p>测试目标：
 * <ul>
 *     <li>验证用例编排是否正确调用领域端口。</li>
 *     <li>验证返回票据的核心字段（documentId/status）是否正确。</li>
 * </ul>
 */
class AcceptUploadApplicationServiceTest {

    @Test
    @DisplayName("handle 应返回 ACCEPTED 且使用端口生成文档 ID")
    void handle_shouldReturnAcceptedTicket() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(generator.nextId()).thenReturn(new DocumentId("doc-001"));
        when(repository.findByKbIdAndFileHash(eq("workspace-a"), eq("kb-x"), eq("hash-a")))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-x")))
                .thenReturn(Optional.of(new KnowledgeBase("kb-x", "workspace-a", "知识库X", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService,
                auditEventRepository);
        AcceptUploadCommand command = new AcceptUploadCommand("a.txt", 10L, "kb-x", "hash-a");

        UploadTicket ticket = service.handle(command);

        assertNotNull(ticket);
        assertEquals("doc-001", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(authorizationService).requireCanContributeKnowledgeBase("kb-x");
        verify(generator, times(1)).nextId();
        verify(repository, times(1)).save(any(Document.class), eq("user-1"));
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("DOCUMENT_UPLOAD_REQUESTED", auditCaptor.getValue().eventType());
        assertEquals("DOCUMENT", auditCaptor.getValue().targetType());
        assertEquals("doc-001", auditCaptor.getValue().targetId());
        assertEquals("user-1", auditCaptor.getValue().actorUserId());
        assertEquals("SUCCESS", auditCaptor.getValue().outcome());
        assertTrue(auditCaptor.getValue().metadata().contains("\"versionResultType\":\"CREATED\""));
    }

    @Test
    @DisplayName("kbId 为空时，流程仍应正常执行并返回受理结果")
    void handle_shouldWork_whenKbIdIsBlank() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(generator.nextId()).thenReturn(new DocumentId("doc-blank-kb"));
        when(repository.findByKbIdAndFileHash(eq("workspace-a"), eq("default"), eq("hash-b")))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("default")))
                .thenReturn(Optional.of(new KnowledgeBase("default", "workspace-a", "default", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService);
        AcceptUploadCommand command = new AcceptUploadCommand("b.txt", 20L, " ", "hash-b");

        UploadTicket ticket = service.handle(command);

        assertEquals("doc-blank-kb", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(generator, times(1)).nextId();

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(repository, times(1)).save(documentCaptor.capture(), eq("user-1"));
        Document saved = documentCaptor.getValue();
        assertEquals("workspace-a", saved.workspaceId());
        assertEquals("default", saved.kbId());
        assertEquals("hash-b", saved.fileHash());
        assertEquals(UploadStatus.UPLOADED, saved.status());
        verify(authorizationService).requireCanContributeKnowledgeBase("default");
    }

    @Test
    @DisplayName("命中相同 kbId+fileHash 时，应复用已有 documentId 且不重复落库")
    void handle_shouldReuseExistingDocument_whenDuplicateUpload() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        Document existing = new Document(
                new DocumentId("doc-existing"),
                "workspace-a",
                "kb-dup",
                "hash-dup",
                "old.txt",
                88L,
                UploadStatus.UPLOADED,
                null,
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
                Instant.now(),
                Instant.now());
        when(repository.findByKbIdAndFileHash(eq("workspace-a"), eq("kb-dup"), eq("hash-dup")))
                .thenReturn(Optional.of(existing));
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-dup")))
                .thenReturn(Optional.of(new KnowledgeBase("kb-dup", "workspace-a", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService);
        AcceptUploadCommand command = new AcceptUploadCommand("new.txt", 99L, "kb-dup", "hash-dup");

        UploadTicket ticket = service.handle(command);

        assertEquals("doc-existing", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(authorizationService).requireCanContributeKnowledgeBase("kb-dup");
        verify(generator, never()).nextId();
        verify(repository, never()).save(any(Document.class), any());
    }

    @Test
    @DisplayName("旧文档删除中同内容重新上传仍应复用原 documentId")
    void handle_shouldReuseExistingDocument_whenDeletingDocumentMatchesDuplicateUpload() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        Document deleting = new Document(
                new DocumentId("doc-deleting"),
                "workspace-a",
                "kb-dup",
                "hash-dup",
                "old.txt",
                88L,
                UploadStatus.DELETING,
                null,
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
                Instant.now(),
                Instant.now());
        when(repository.findByKbIdAndFileHash(eq("workspace-a"), eq("kb-dup"), eq("hash-dup")))
                .thenReturn(Optional.of(deleting));
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-dup")))
                .thenReturn(Optional.of(new KnowledgeBase("kb-dup", "workspace-a", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService,
                auditEventRepository);

        UploadTicket ticket = service.handle(new AcceptUploadCommand("same.txt", 99L, "kb-dup", "hash-dup"));

        assertEquals("doc-deleting", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(generator, never()).nextId();
        verify(repository, never()).save(any(Document.class), any());
    }

    @Test
    @DisplayName("旧文档删除后同内容重新上传应生成新 documentId，且不继承旧文档级授权")
    void handle_shouldCreateNewDocument_whenDeletedDocumentIsExcludedFromDuplicateLookup() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(generator.nextId()).thenReturn(new DocumentId("doc-new-after-delete"));
        when(repository.findByKbIdAndFileHash(eq("workspace-a"), eq("kb-dup"), eq("hash-dup")))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-dup")))
                .thenReturn(Optional.of(new KnowledgeBase(
                        "kb-dup",
                        "workspace-a",
                        "知识库",
                        "",
                        KnowledgeBaseStatus.ACTIVE,
                        Instant.now(),
                        Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService,
                auditEventRepository);

        UploadTicket ticket = service.handle(new AcceptUploadCommand("same.txt", 99L, "kb-dup", "hash-dup"));

        assertEquals("doc-new-after-delete", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(authorizationService).requireCanContributeKnowledgeBase("kb-dup");
        verify(generator, times(1)).nextId();
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(documentCaptor.capture(), eq("user-1"));
        assertEquals("doc-new-after-delete", documentCaptor.getValue().documentId().value());
        assertEquals("hash-dup", documentCaptor.getValue().fileHash());
    }

    @Test
    @DisplayName("知识库不存在时应抛出未找到异常")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-missing"))).thenReturn(Optional.empty());

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService);

        org.junit.jupiter.api.Assertions.assertThrows(
                KnowledgeBaseNotFoundException.class,
                () -> service.handle(new AcceptUploadCommand("x.txt", 1L, "kb-missing", "hash-x")));
    }

    @Test
    @DisplayName("知识库停用时应抛出冲突异常")
    void handle_shouldThrow_whenKnowledgeBaseInactive() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-inactive")))
                .thenReturn(Optional.of(new KnowledgeBase("kb-inactive", "workspace-a", "禁用库", "", KnowledgeBaseStatus.INACTIVE, Instant.now(), Instant.now())));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService);

        org.junit.jupiter.api.Assertions.assertThrows(
                KnowledgeBaseInactiveException.class,
                () -> service.handle(new AcceptUploadCommand("x.txt", 1L, "kb-inactive", "hash-x")));
    }

    @Test
    @DisplayName("无知识库贡献权限时应拒绝上传且不生成文档")
    void handle_shouldDeny_whenUserCannotContributeKnowledgeBase() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        DocumentRepository repository = Mockito.mock(DocumentRepository.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        Mockito.doThrow(new AccessDeniedException("knowledge base contribute access denied"))
                .when(authorizationService)
                .requireCanContributeKnowledgeBase("kb-reader");
        AcceptUploadApplicationService service = new AcceptUploadApplicationService(
                generator,
                repository,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService);

        assertThrows(
                AccessDeniedException.class,
                () -> service.handle(new AcceptUploadCommand("x.txt", 1L, "kb-reader", "hash-x")));

        verify(knowledgeBaseRepository, never()).findByKbId(any(), any());
        verify(repository, never()).findByKbIdAndFileHash(any(), any(), any());
        verify(generator, never()).nextId();
        verify(repository, never()).save(any(Document.class), any());
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
