package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ListMemberKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListMemberKnowledgeBaseGrantsApplicationService implements ListMemberKnowledgeBaseGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final KnowledgeBaseGrantManagementRepository grantRepository;

    public ListMemberKnowledgeBaseGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            WorkspaceMemberRepository workspaceMemberRepository,
            KnowledgeBaseGrantManagementRepository grantRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.grantRepository = grantRepository;
    }

    @Override
    public List<KnowledgeBaseGrantResult> handle(String userId) {
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        requireUserId(userId))
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + requireUserId(userId)));
        workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());
        return grantRepository.findActiveGrantsByUser(currentUser.workspaceId(), member.userId()).stream()
                .map(grant -> new KnowledgeBaseGrantResult(
                        grant.workspaceId(),
                        grant.kbId(),
                        grant.userId(),
                        grant.username(),
                        grant.displayName(),
                        grant.role(),
                        grant.status()))
                .toList();
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        return userId.trim();
    }
}
