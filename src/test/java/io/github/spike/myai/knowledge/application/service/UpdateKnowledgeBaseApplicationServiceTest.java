package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.knowledge.application.command.UpdateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class UpdateKnowledgeBaseApplicationServiceTest {

    @Test
    @DisplayName("编辑知识库时应更新名称描述和状态")
    void handle_shouldUpdateKnowledgeBase() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser());
        when(repository.findByKbId(eq("workspace-a"), eq("kb-1"))).thenReturn(Optional.of(
                new KnowledgeBase("kb-1", "workspace-a", "旧名称", "旧描述", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(repository.listKnowledgeBases(eq("workspace-a"))).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-1", "workspace-a", "新名称", "新描述", KnowledgeBaseStatus.INACTIVE, 2)));
        UpdateKnowledgeBaseApplicationService service =
                new UpdateKnowledgeBaseApplicationService(repository, currentUserProvider, authorizationService);

        var result = service.handle(new UpdateKnowledgeBaseCommand("kb-1", "新名称", "新描述", KnowledgeBaseStatus.INACTIVE));

        assertEquals("新名称", result.name());
        assertEquals("新描述", result.description());
        assertEquals("INACTIVE", result.status());
        assertEquals(2L, result.indexedDocumentCount());
        verify(authorizationService).requireCanManageKnowledgeBase("kb-1");
        verify(repository).save(any());
    }

    @Test
    @DisplayName("编辑不存在知识库时应抛出未找到异常")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser());
        when(repository.findByKbId(eq("workspace-a"), eq("kb-missing"))).thenReturn(Optional.empty());
        UpdateKnowledgeBaseApplicationService service =
                new UpdateKnowledgeBaseApplicationService(repository, currentUserProvider, authorizationService);

        assertThrows(
                KnowledgeBaseNotFoundException.class,
                () -> service.handle(new UpdateKnowledgeBaseCommand("kb-missing", "新名称", null, null)));
    }

    @Test
    @DisplayName("无知识库管理权限时编辑知识库应被拒绝")
    void handle_shouldDeny_whenUserCannotManageKnowledgeBase() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        Mockito.doThrow(new AccessDeniedException("knowledge base manage access denied"))
                .when(authorizationService)
                .requireCanManageKnowledgeBase("kb-1");
        UpdateKnowledgeBaseApplicationService service =
                new UpdateKnowledgeBaseApplicationService(repository, currentUserProvider, authorizationService);

        assertThrows(
                AccessDeniedException.class,
                () -> service.handle(new UpdateKnowledgeBaseCommand("kb-1", "新名称", null, null)));

        verify(repository, never()).findByKbId(any(), any());
        verify(repository, never()).save(any());
    }

    private static CurrentUser currentUser() {
        return new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER);
    }
}
