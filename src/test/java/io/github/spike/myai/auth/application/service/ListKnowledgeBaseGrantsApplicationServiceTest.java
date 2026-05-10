package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseGrant;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListKnowledgeBaseGrantsApplicationServiceTest {

    @Test
    @DisplayName("管理员查询知识库授权列表时应返回 ACTIVE grant")
    void handle_shouldReturnKnowledgeBaseGrants() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase("kb-1", "default", "知识库", "", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(grantRepository.findActiveGrants("default", "kb-1")).thenReturn(List.of(
                new KnowledgeBaseGrant("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_READER, "ACTIVE")));
        ListKnowledgeBaseGrantsApplicationService service = new ListKnowledgeBaseGrantsApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                grantRepository);

        var result = service.handle("kb-1");

        assertEquals(1, result.size());
        assertEquals("user-2", result.getFirst().userId());
        assertEquals(KnowledgeBaseRole.KB_READER, result.getFirst().role());
        verify(grantRepository).findActiveGrants("default", "kb-1");
    }

    @Test
    @DisplayName("知识库不存在时查询授权列表应返回 404 语义异常")
    void handle_shouldThrowNotFound_whenKnowledgeBaseMissing() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(knowledgeBaseRepository.findByKbId("default", "kb-missing")).thenReturn(Optional.empty());
        ListKnowledgeBaseGrantsApplicationService service = new ListKnowledgeBaseGrantsApplicationService(
                authorizationService,
                knowledgeBaseRepository,
                grantRepository);

        assertThrows(ManagedKnowledgeBaseNotFoundException.class, () -> service.handle("kb-missing"));
    }
}
