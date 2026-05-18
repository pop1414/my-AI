package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ListMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListMemberDocumentGrantsApplicationService implements ListMemberDocumentGrantsUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DocumentRepository documentRepository;
    private final DocumentGrantManagementRepository grantRepository;

    public ListMemberDocumentGrantsApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            DocumentGrantKnowledgeBaseGuard documentGrantKnowledgeBaseGuard,
            WorkspaceMemberRepository workspaceMemberRepository,
            DocumentRepository documentRepository,
            DocumentGrantManagementRepository grantRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.documentGrantKnowledgeBaseGuard = documentGrantKnowledgeBaseGuard;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.documentRepository = documentRepository;
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
                .filter(grant -> hasGrantedKnowledgeBase(currentUser.workspaceId(), member.userId(), grant.documentId()))
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

    /**
     * 判断成员是否仍拥有文档所属知识库授权。
     *
     * <p>历史脏数据可能存在没有父级知识库授权的文档授权，列表场景需要隐藏这些
     * 已失效的文档授权，避免前端再次把它们作为期望授权提交。
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标成员 ID
     * @param documentId  文档 ID
     * @return {@code true} 表示文档所属知识库仍对该成员有效授权
     */
    private boolean hasGrantedKnowledgeBase(String workspaceId, String userId, String documentId) {
        return documentRepository.findById(workspaceId, new DocumentId(documentId))
                .map(document -> documentGrantKnowledgeBaseGuard.hasMemberKnowledgeBaseGrant(
                        workspaceId,
                        userId,
                        document.kbId()))
                .orElse(false);
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        return userId.trim();
    }
}
