package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.command.CreateManagedMemberCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class CreateManagedMemberApplicationServiceTest {

    @Test
    @DisplayName("新增成员成功时应创建账号并写入初始知识库授权")
    void handle_shouldCreateMemberAndSeedKnowledgeBaseGrant() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository managedAccountRepository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(managedAccountRepository.existsUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(knowledgeBaseRepository.findByKbId("default", "kb-1"))
                .thenReturn(Optional.of(new KnowledgeBase(
                        "kb-1",
                        "default",
                        "知识库",
                        "",
                        KnowledgeBaseStatus.ACTIVE,
                        Instant.now(),
                        Instant.now())));
        when(managedAccountRepository.createAccount(
                eq("default"),
                eq("bob"),
                eq("Bob"),
                eq("encoded-secret"),
                eq(WorkspaceRole.WORKSPACE_MEMBER),
                any()))
                .thenReturn(new ManagedAccount(
                        "user-2",
                        "bob",
                        "Bob",
                        "ACTIVE",
                        "default",
                        WorkspaceRole.WORKSPACE_MEMBER,
                        "ACTIVE",
                        0,
                        null));

        CreateManagedMemberApplicationService service =
                new CreateManagedMemberApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        managedAccountRepository,
                        passwordEncoder,
                        knowledgeBaseRepository,
                        grantRepository,
                        auditEventRepository);

        var result = service.handle(new CreateManagedMemberCommand(
                "bob",
                "Bob",
                "secret123",
                List.of(new CreateManagedMemberCommand.InitialKnowledgeBaseGrantCommand(
                        "kb-1",
                        "KB_READER"))));

        assertEquals("user-2", result.userId());
        verify(grantRepository).saveGrant(
                eq("default"),
                eq("kb-1"),
                eq("user-2"),
                eq(io.github.spike.myai.auth.domain.model.KnowledgeBaseRole.KB_READER),
                any());
        verify(auditEventRepository).save(any());
    }

    @Test
    @DisplayName("用户名冲突时新增成员应失败")
    void handle_shouldThrowConflictWhenUsernameExists() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        WorkspaceGovernanceGuard workspaceGovernanceGuard = Mockito.mock(WorkspaceGovernanceGuard.class);
        ManagedAccountRepository managedAccountRepository = Mockito.mock(ManagedAccountRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        KnowledgeBaseGrantManagementRepository grantRepository = Mockito.mock(KnowledgeBaseGrantManagementRepository.class);
        AuditEventRepository auditEventRepository = Mockito.mock(AuditEventRepository.class);
        when(authorizationService.requireCanManageWorkspace())
                .thenReturn(new CurrentUser("admin-1", "alice", "default", WorkspaceRole.WORKSPACE_ADMIN));
        when(managedAccountRepository.existsUsername("bob")).thenReturn(true);

        CreateManagedMemberApplicationService service =
                new CreateManagedMemberApplicationService(
                        authorizationService,
                        workspaceGovernanceGuard,
                        managedAccountRepository,
                        passwordEncoder,
                        knowledgeBaseRepository,
                        grantRepository,
                        auditEventRepository);

        assertThrows(
                ManagedAccountUsernameConflictException.class,
                () -> service.handle(new CreateManagedMemberCommand(
                        "bob",
                        "Bob",
                        "secret123",
                        List.of(new CreateManagedMemberCommand.InitialKnowledgeBaseGrantCommand(
                                "kb-1",
                                "KB_READER")))));
    }
}
