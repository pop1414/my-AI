package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ListMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListMemberDocumentGrantsApplicationService implements ListMemberDocumentGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DocumentGrantManagementRepository grantRepository;

    public ListMemberDocumentGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            WorkspaceMemberRepository workspaceMemberRepository,
            DocumentGrantManagementRepository grantRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.grantRepository = grantRepository;
    }

    @Override
    public List<DocumentGrantResult> handle(String userId) {
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        requireUserId(userId))
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("workspace member not found: " + requireUserId(userId)));
        workspaceGovernanceGuard.requireCanManageGrantTarget(currentUser, member.workspaceRole());
        return grantRepository.findActiveGrantsByUser(currentUser.workspaceId(), member.userId()).stream()
                .map(grant -> new DocumentGrantResult(
                        grant.workspaceId(),
                        grant.documentId(),
                        grant.userId(),
                        grant.username(),
                        grant.displayName(),
                        grant.permission(),
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
