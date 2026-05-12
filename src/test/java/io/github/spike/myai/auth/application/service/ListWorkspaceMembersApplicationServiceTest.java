package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class ListWorkspaceMembersApplicationServiceTest {

    @Test
    @DisplayName("管理员查询成员列表时应返回有效成员结果")
    void handle_shouldReturnWorkspaceMembers() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findActiveMembers("workspace-a")).thenReturn(List.of(
                new WorkspaceMember("user-1", "alice", "Alice", "workspace-a", WorkspaceRole.WORKSPACE_ADMIN, "ACTIVE"),
                new WorkspaceMember("user-2", "bob", "Bob", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));
        ListWorkspaceMembersApplicationService service =
                new ListWorkspaceMembersApplicationService(authorizationService, repository);

        var result = service.handle();

        assertEquals(2, result.size());
        assertEquals("user-1", result.getFirst().userId());
        assertEquals(WorkspaceRole.WORKSPACE_ADMIN, result.getFirst().workspaceRole());
        verify(repository).findActiveMembers("workspace-a");
    }

    @Test
    @DisplayName("无工作区管理权限时查询成员列表应被拒绝")
    void handle_shouldDeny_whenUserCannotManageWorkspace() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceMemberRepository repository = Mockito.mock(WorkspaceMemberRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenThrow(new AccessDeniedException("workspace manage access denied"));
        ListWorkspaceMembersApplicationService service =
                new ListWorkspaceMembersApplicationService(authorizationService, repository);

        assertThrows(AccessDeniedException.class, service::handle);

        verify(repository, never()).findActiveMembers("workspace-a");
    }
}
