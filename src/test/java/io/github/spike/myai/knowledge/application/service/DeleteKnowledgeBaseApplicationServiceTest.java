package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
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

class DeleteKnowledgeBaseApplicationServiceTest {

    @Test
    @DisplayName("删除知识库时应标记为 DELETED 并写入审计")
    void handle_shouldMarkDeletedAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace()).thenReturn(currentUser());
        when(repository.findByKbIdIncludingDeleted(eq("workspace-a"), eq("kb-1"))).thenReturn(Optional.of(
                new KnowledgeBase("kb-1", "workspace-a", "知识库A", "描述", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        DeleteKnowledgeBaseApplicationService service =
                new DeleteKnowledgeBaseApplicationService(authorizationService, repository, auditEventRepository);

        service.handle(" kb-1 ");

        ArgumentCaptor<KnowledgeBase> knowledgeBaseCaptor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(repository).save(knowledgeBaseCaptor.capture());
        assertEquals(KnowledgeBaseStatus.DELETED, knowledgeBaseCaptor.getValue().status());

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        AuditEvent auditEvent = auditCaptor.getValue();
        assertEquals("workspace-a", auditEvent.workspaceId());
        assertEquals("user-1", auditEvent.actorUserId());
        assertEquals("alice", auditEvent.actorUsername());
        assertEquals("KNOWLEDGE_BASE_DELETED", auditEvent.eventType());
        assertEquals("KNOWLEDGE_BASE", auditEvent.targetType());
        assertEquals("kb-1", auditEvent.targetId());
        assertEquals("SUCCESS", auditEvent.outcome());
        assertEquals(
                """
                {"kbId":"kb-1","name":"知识库A","previousStatus":"ACTIVE"}
                """,
                auditEvent.metadata());
    }

    @Test
    @DisplayName("删除已删除知识库时应幂等返回且不重复写审计")
    void handle_shouldBeIdempotent_whenKnowledgeBaseAlreadyDeleted() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace()).thenReturn(currentUser());
        when(repository.findByKbIdIncludingDeleted(eq("workspace-a"), eq("kb-1"))).thenReturn(Optional.of(
                new KnowledgeBase("kb-1", "workspace-a", "知识库A", "描述", KnowledgeBaseStatus.DELETED, Instant.now(), Instant.now())));
        DeleteKnowledgeBaseApplicationService service =
                new DeleteKnowledgeBaseApplicationService(authorizationService, repository, auditEventRepository);

        service.handle("kb-1");

        verify(repository, never()).save(any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除不存在知识库时应抛出未找到异常且不写审计")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace()).thenReturn(currentUser());
        when(repository.findByKbIdIncludingDeleted(eq("workspace-a"), eq("kb-missing"))).thenReturn(Optional.empty());
        DeleteKnowledgeBaseApplicationService service =
                new DeleteKnowledgeBaseApplicationService(authorizationService, repository, auditEventRepository);

        assertThrows(KnowledgeBaseNotFoundException.class, () -> service.handle("kb-missing"));

        verify(repository, never()).save(any());
        verify(auditEventRepository, never()).save(any());
    }

    private static CurrentUser currentUser() {
        return new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN);
    }
}
