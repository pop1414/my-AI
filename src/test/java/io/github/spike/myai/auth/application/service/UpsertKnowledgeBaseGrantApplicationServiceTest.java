package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.UpsertKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UpsertKnowledgeBaseGrantApplicationServiceTest {

    @Test
    @DisplayName("授予知识库授权时应写入 grant 与审计")
    void handle_shouldSaveGrantAndWriteAudit() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        when(grantRepository.findActiveGrant("default", "kb-1", "user-2")).thenReturn(Optional.empty());
        UpsertKnowledgeBaseGrantApplicationService service = new UpsertKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        var result = service.handle(new UpsertKnowledgeBaseGrantCommand("kb-1", "user-2", "KB_READER"));

        assertEquals(KnowledgeBaseRole.KB_READER, result.role());
        verify(grantRepository).saveGrant(eq("default"), eq("kb-1"), eq("user-2"), eq(KnowledgeBaseRole.KB_READER), any());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(auditCaptor.capture());
        assertEquals("KNOWLEDGE_BASE_GRANT_UPSERTED", auditCaptor.getValue().eventType());
    }

    @Test
    @DisplayName("授权角色未变化时应直接返回且不重复写入")
    void handle_shouldReturnExistingGrant_whenRoleUnchanged() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(workspaceMemberRepository.findActiveMember("default", "user-2"))
                .thenReturn(Optional.of(new WorkspaceMember("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        when(grantRepository.findActiveGrant("default", "kb-1", "user-2"))
                .thenReturn(Optional.of(new KnowledgeBaseGrant("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_READER, "ACTIVE")));
        UpsertKnowledgeBaseGrantApplicationService service = new UpsertKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        var result = service.handle(new UpsertKnowledgeBaseGrantCommand("kb-1", "user-2", "KB_READER"));

        assertEquals(KnowledgeBaseRole.KB_READER, result.role());
        verify(grantRepository, never()).saveGrant(eq("default"), eq("kb-1"), eq("user-2"), eq(KnowledgeBaseRole.KB_READER), any());
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("知识库不存在时授予授权应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenKnowledgeBaseMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-missing")).thenReturn(Optional.empty());
        UpsertKnowledgeBaseGrantApplicationService service = new UpsertKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                ManagedKnowledgeBaseNotFoundException.class,
                () -> service.handle(new UpsertKnowledgeBaseGrantCommand("kb-missing", "user-2", "KB_READER")));
    }

    @Test
    @DisplayName("目标成员不存在时授予授权应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenMemberMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        WorkspaceMemberRepository workspaceMemberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(workspaceMemberRepository.findActiveMember("default", "user-missing")).thenReturn(Optional.empty());
        UpsertKnowledgeBaseGrantApplicationService service = new UpsertKnowledgeBaseGrantApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                workspaceMemberRepository,
                grantRepository,
                auditEventRepository);

        assertThrows(
                WorkspaceMemberNotFoundException.class,
                () -> service.handle(new UpsertKnowledgeBaseGrantCommand("kb-1", "user-missing", "KB_READER")));
    }
}
