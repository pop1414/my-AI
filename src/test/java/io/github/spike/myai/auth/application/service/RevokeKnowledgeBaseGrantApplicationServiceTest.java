package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.RevokeKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.KnowledgeBaseGrantNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RevokeKnowledgeBaseGrantApplicationServiceTest {

    @Test
    @DisplayName("回收知识库授权时应更新状态并写入审计")
    void handle_shouldDisableGrantAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(grantRepository.findActiveGrant("default", "kb-1", "user-2"))
                .thenReturn(Optional.of(new KnowledgeBaseGrant("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_READER, "ACTIVE")));
        when(grantRepository.disableGrant(eq("default"), eq("kb-1"), eq("user-2"), any())).thenReturn(true);
        RevokeKnowledgeBaseGrantApplicationService service = new RevokeKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                grantRepository,
                auditEventRepository);

        service.handle(new RevokeKnowledgeBaseGrantCommand("kb-1", "user-2"));

        verify(grantRepository).disableGrant(eq("default"), eq("kb-1"), eq("user-2"), any());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assert auditCaptor.getValue().eventType().equals("KNOWLEDGE_BASE_GRANT_REVOKED");
    }

    @Test
    @DisplayName("授权不存在时回收应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenGrantMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(grantRepository.findActiveGrant("default", "kb-1", "user-missing")).thenReturn(Optional.empty());
        RevokeKnowledgeBaseGrantApplicationService service = new RevokeKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                KnowledgeBaseGrantNotFoundException.class,
                () -> service.handle(new RevokeKnowledgeBaseGrantCommand("kb-1", "user-missing")));
    }
}
