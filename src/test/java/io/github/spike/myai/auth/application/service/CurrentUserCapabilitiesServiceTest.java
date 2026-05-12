package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CurrentUserCapabilitiesServiceTest {

    @Test
    @DisplayName("工作区管理员应拥有全部能力位")
    void resolve_shouldAllowOwnerAndAdmin() {
        AuthorizationGrantRepository repository = Mockito.mock(AuthorizationGrantRepository.class);
        CurrentUserCapabilitiesService service = new CurrentUserCapabilitiesService(repository);

        var owner = service.resolve(new CurrentUserResult("u1", "alice", "Alice", "default", WorkspaceRole.WORKSPACE_OWNER));
        assertTrue(owner.canAccessAdmin());
        assertTrue(owner.canAccessKnowledge());
        assertTrue(owner.canAccessDocumentList());
        assertTrue(owner.canUploadDocument());
        assertTrue(owner.canAskQuestion());

        var admin = service.resolve(new CurrentUserResult("u1", "alice", "Alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        assertTrue(admin.canAccessAdmin());
        assertTrue(admin.canAccessKnowledge());
        assertTrue(admin.canAccessDocumentList());
        assertTrue(admin.canUploadDocument());
        assertTrue(admin.canAskQuestion());
    }

    @Test
    @DisplayName("KB_CONTRIBUTOR 应允许列表、上传、知识库与问答能力")
    void resolve_shouldAllowContributor() {
        AuthorizationGrantRepository repository = Mockito.mock(AuthorizationGrantRepository.class);
        when(repository.listGrantedKnowledgeBaseRoles("default", "u1"))
                .thenReturn(Set.of(KnowledgeBaseRole.KB_CONTRIBUTOR));
        CurrentUserCapabilitiesService service = new CurrentUserCapabilitiesService(repository);

        var capabilities = service.resolve("u1", "default", WorkspaceRole.WORKSPACE_MEMBER);

        assertFalse(capabilities.canAccessAdmin());
        assertTrue(capabilities.canAccessKnowledge());
        assertTrue(capabilities.canAccessDocumentList());
        assertTrue(capabilities.canUploadDocument());
        assertTrue(capabilities.canAskQuestion());
    }

    @Test
    @DisplayName("KB_READER 应允许列表、知识库与问答，但不允许上传")
    void resolve_shouldAllowReader() {
        AuthorizationGrantRepository repository = Mockito.mock(AuthorizationGrantRepository.class);
        when(repository.listGrantedKnowledgeBaseRoles("default", "u1"))
                .thenReturn(Set.of(KnowledgeBaseRole.KB_READER));
        CurrentUserCapabilitiesService service = new CurrentUserCapabilitiesService(repository);

        var capabilities = service.resolve("u1", "default", WorkspaceRole.WORKSPACE_MEMBER);

        assertFalse(capabilities.canAccessAdmin());
        assertTrue(capabilities.canAccessKnowledge());
        assertTrue(capabilities.canAccessDocumentList());
        assertFalse(capabilities.canUploadDocument());
        assertTrue(capabilities.canAskQuestion());
    }

    @Test
    @DisplayName("KB_ASKER 应允许知识库与问答，但不允许列表与上传")
    void resolve_shouldAllowAsker() {
        AuthorizationGrantRepository repository = Mockito.mock(AuthorizationGrantRepository.class);
        when(repository.listGrantedKnowledgeBaseRoles("default", "u1"))
                .thenReturn(Set.of(KnowledgeBaseRole.KB_ASKER));
        CurrentUserCapabilitiesService service = new CurrentUserCapabilitiesService(repository);

        var capabilities = service.resolve("u1", "default", WorkspaceRole.WORKSPACE_MEMBER);

        assertFalse(capabilities.canAccessAdmin());
        assertTrue(capabilities.canAccessKnowledge());
        assertFalse(capabilities.canAccessDocumentList());
        assertFalse(capabilities.canUploadDocument());
        assertTrue(capabilities.canAskQuestion());
    }

    @Test
    @DisplayName("无任何 grant 的普通成员不应拥有业务能力")
    void resolve_shouldDenyWhenNoGrant() {
        AuthorizationGrantRepository repository = Mockito.mock(AuthorizationGrantRepository.class);
        when(repository.listGrantedKnowledgeBaseRoles("default", "u1")).thenReturn(Set.of());
        CurrentUserCapabilitiesService service = new CurrentUserCapabilitiesService(repository);

        var capabilities = service.resolve("u1", "default", WorkspaceRole.WORKSPACE_MEMBER);

        assertFalse(capabilities.canAccessAdmin());
        assertFalse(capabilities.canAccessKnowledge());
        assertFalse(capabilities.canAccessDocumentList());
        assertFalse(capabilities.canUploadDocument());
        assertFalse(capabilities.canAskQuestion());
    }
}
