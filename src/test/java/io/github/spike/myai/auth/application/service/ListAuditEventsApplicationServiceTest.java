package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.query.ListAuditEventsQuery;
import io.github.spike.myai.auth.domain.model.AuditEventEntry;
import io.github.spike.myai.auth.domain.model.AuditEventPage;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventQueryRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class ListAuditEventsApplicationServiceTest {

    @Test
    @DisplayName("管理员查询审计事件时应返回分页结果")
    void handle_shouldReturnAuditEventPage() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventQueryRepository repository = Mockito.mock(AuditEventQueryRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("user-admin", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(repository.findPage(Mockito.eq("default"), Mockito.any())).thenReturn(new AuditEventPage(
                List.of(new AuditEventEntry(
                        1001L,
                        "default",
                        "user-admin",
                        "alice",
                        "WORKSPACE_MEMBER_ROLE_UPDATED",
                        "WORKSPACE_MEMBERSHIP",
                        "user-2",
                        "SUCCESS",
                        "",
                        "{\"newRole\":\"WORKSPACE_ADMIN\"}",
                        Instant.parse("2026-05-10T03:00:00Z"))),
                1L,
                20,
                0));
        ListAuditEventsApplicationService service = new ListAuditEventsApplicationService(
                authorizationService,
                repository);

        var result = service.handle(new ListAuditEventsQuery(null, null, null, null, null, null, null, 20, 0));

        assertEquals(1, result.items().size());
        assertEquals(1001L, result.items().getFirst().auditEventId());
        assertEquals("WORKSPACE_MEMBER_ROLE_UPDATED", result.items().getFirst().eventType());
        verify(repository).findPage(Mockito.eq("default"), Mockito.any());
    }

    @Test
    @DisplayName("无工作区管理权限时查询审计事件应被拒绝")
    void handle_shouldDeny_whenUserCannotManageWorkspace() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AuditEventQueryRepository repository = Mockito.mock(AuditEventQueryRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenThrow(new AccessDeniedException("workspace manage access denied"));
        ListAuditEventsApplicationService service = new ListAuditEventsApplicationService(
                authorizationService,
                repository);

        assertThrows(
                AccessDeniedException.class,
                () -> service.handle(new ListAuditEventsQuery(null, null, null, null, null, null, null, 20, 0)));
    }
}
