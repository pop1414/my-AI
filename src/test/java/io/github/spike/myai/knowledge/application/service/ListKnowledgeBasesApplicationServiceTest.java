package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListKnowledgeBasesApplicationServiceTest {

    @Test
    @DisplayName("存在知识库主数据时应返回带统计的列表")
    void handle_shouldReturnKnowledgeBases() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationGrantRepository authorizationGrantRepository = Mockito.mock(AuthorizationGrantRepository.class);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.listKnowledgeBases("workspace-a")).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-a", "workspace-a", "知识库A", "desc-a", KnowledgeBaseStatus.ACTIVE, 3),
                new KnowledgeBaseSummary("kb-b", "workspace-a", "知识库B", "", KnowledgeBaseStatus.INACTIVE, 0)));
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(
                repository,
                currentUserProvider,
                authorizationGrantRepository);

        var result = service.handle();

        assertEquals(2, result.size());
        assertEquals("kb-a", result.get(0).id());
        assertEquals("知识库A", result.get(0).name());
        assertEquals("desc-a", result.get(0).description());
        assertEquals("ACTIVE", result.get(0).status());
        assertEquals(3, result.get(0).indexedDocumentCount());
        assertEquals("INACTIVE", result.get(1).status());
    }

    @Test
    @DisplayName("普通成员查询知识库列表时应只返回自己具备知识库授权的条目")
    void handle_shouldReturnOnlyGrantedKnowledgeBases_whenWorkspaceMember() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationGrantRepository authorizationGrantRepository = Mockito.mock(AuthorizationGrantRepository.class);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-2", "bob", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        when(repository.listKnowledgeBases("workspace-a")).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-a", "workspace-a", "知识库A", "desc-a", KnowledgeBaseStatus.ACTIVE, 3),
                new KnowledgeBaseSummary("kb-b", "workspace-a", "知识库B", "desc-b", KnowledgeBaseStatus.ACTIVE, 5),
                new KnowledgeBaseSummary("kb-c", "workspace-a", "知识库C", "", KnowledgeBaseStatus.INACTIVE, 0)));
        when(authorizationGrantRepository.listGrantedKnowledgeBaseIds("workspace-a", "user-2"))
                .thenReturn(Set.of("kb-b", "kb-c"));
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(
                repository,
                currentUserProvider,
                authorizationGrantRepository);

        var result = service.handle();

        assertEquals(2, result.size());
        assertEquals("kb-b", result.get(0).id());
        assertEquals("kb-c", result.get(1).id());
    }

    @Test
    @DisplayName("普通成员无任何知识库授权时应返回空列表")
    void handle_shouldReturnEmpty_whenWorkspaceMemberHasNoKnowledgeBaseGrant() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        AuthorizationGrantRepository authorizationGrantRepository = Mockito.mock(AuthorizationGrantRepository.class);
        when(currentUserProvider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-2", "bob", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        when(repository.listKnowledgeBases("workspace-a")).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-a", "workspace-a", "知识库A", "desc-a", KnowledgeBaseStatus.ACTIVE, 3)));
        when(authorizationGrantRepository.listGrantedKnowledgeBaseIds("workspace-a", "user-2"))
                .thenReturn(Set.of());
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(
                repository,
                currentUserProvider,
                authorizationGrantRepository);

        var result = service.handle();

        assertEquals(0, result.size());
    }
}
