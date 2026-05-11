package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.ReplaceMemberDocumentGrantsCommand;
import io.github.spike.myai.auth.application.command.ReplaceMemberKnowledgeBaseGrantsCommand;
import io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.ListMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ListMemberKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ListWorkspaceMembersUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateWorkspaceMemberRoleUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.DocumentGrantResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.KnowledgeBaseGrantResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.ReplaceMemberDocumentGrantsRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.ReplaceMemberKnowledgeBaseGrantsRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.UpdateWorkspaceMemberRoleRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.WorkspaceMemberResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 工作区成员治理 REST 控制器。
 *
 * <p>提供成员列表、角色调整、成员维度知识库授权、成员维度文档授权的治理接口。
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/members")
public class WorkspaceMemberAdminController {

    private final ListWorkspaceMembersUseCase listWorkspaceMembersUseCase;
    private final ListMemberKnowledgeBaseGrantsUseCase listMemberKnowledgeBaseGrantsUseCase;
    private final ReplaceMemberKnowledgeBaseGrantsUseCase replaceMemberKnowledgeBaseGrantsUseCase;
    private final ListMemberDocumentGrantsUseCase listMemberDocumentGrantsUseCase;
    private final ReplaceMemberDocumentGrantsUseCase replaceMemberDocumentGrantsUseCase;
    private final UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase;

    public WorkspaceMemberAdminController(
            ListWorkspaceMembersUseCase listWorkspaceMembersUseCase,
            ListMemberKnowledgeBaseGrantsUseCase listMemberKnowledgeBaseGrantsUseCase,
            ReplaceMemberKnowledgeBaseGrantsUseCase replaceMemberKnowledgeBaseGrantsUseCase,
            ListMemberDocumentGrantsUseCase listMemberDocumentGrantsUseCase,
            ReplaceMemberDocumentGrantsUseCase replaceMemberDocumentGrantsUseCase,
            UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase) {
        this.listWorkspaceMembersUseCase = listWorkspaceMembersUseCase;
        this.listMemberKnowledgeBaseGrantsUseCase = listMemberKnowledgeBaseGrantsUseCase;
        this.replaceMemberKnowledgeBaseGrantsUseCase = replaceMemberKnowledgeBaseGrantsUseCase;
        this.listMemberDocumentGrantsUseCase = listMemberDocumentGrantsUseCase;
        this.replaceMemberDocumentGrantsUseCase = replaceMemberDocumentGrantsUseCase;
        this.updateWorkspaceMemberRoleUseCase = updateWorkspaceMemberRoleUseCase;
    }

    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<WorkspaceMemberResponse> listMembers() {
        return listWorkspaceMembersUseCase.handle().stream()
                .map(WorkspaceMemberAdminController::toMemberResponse)
                .toList();
    }

    @PatchMapping(value = "/{userId}/role", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkspaceMemberResponse updateMemberRole(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) UpdateWorkspaceMemberRoleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            return toMemberResponse(updateWorkspaceMemberRoleUseCase.handle(
                    new UpdateWorkspaceMemberRoleCommand(userId, request.workspaceRole())));
        } catch (WorkspaceMemberNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/{userId}/knowledge-base-grants", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnowledgeBaseGrantResponse> listMemberKnowledgeBaseGrants(
            @PathVariable("userId") String userId) {
        try {
            return listMemberKnowledgeBaseGrantsUseCase.handle(userId).stream()
                    .map(WorkspaceMemberAdminController::toKnowledgeBaseGrantResponse)
                    .toList();
        } catch (WorkspaceMemberNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping(value = "/{userId}/knowledge-base-grants:batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnowledgeBaseGrantResponse> replaceMemberKnowledgeBaseGrants(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) ReplaceMemberKnowledgeBaseGrantsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            return replaceMemberKnowledgeBaseGrantsUseCase.handle(new ReplaceMemberKnowledgeBaseGrantsCommand(
                            userId,
                            request.assignments() == null
                                    ? List.of()
                                    : request.assignments().stream()
                                            .map(item -> new ReplaceMemberKnowledgeBaseGrantsCommand.Assignment(
                                                    item.kbId(),
                                                    item.role()))
                                            .toList()))
                    .stream()
                    .map(WorkspaceMemberAdminController::toKnowledgeBaseGrantResponse)
                    .toList();
        } catch (WorkspaceMemberNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping(value = "/{userId}/document-grants", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentGrantResponse> listMemberDocumentGrants(
            @PathVariable("userId") String userId) {
        try {
            return listMemberDocumentGrantsUseCase.handle(userId).stream()
                    .map(WorkspaceMemberAdminController::toDocumentGrantResponse)
                    .toList();
        } catch (WorkspaceMemberNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping(value = "/{userId}/document-grants:batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentGrantResponse> replaceMemberDocumentGrants(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) ReplaceMemberDocumentGrantsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            return replaceMemberDocumentGrantsUseCase.handle(new ReplaceMemberDocumentGrantsCommand(
                            userId,
                            request.assignments() == null
                                    ? List.of()
                                    : request.assignments().stream()
                                            .map(item -> new ReplaceMemberDocumentGrantsCommand.Assignment(
                                                    item.documentId(),
                                                    item.permission()))
                                            .toList()))
                    .stream()
                    .map(WorkspaceMemberAdminController::toDocumentGrantResponse)
                    .toList();
        } catch (WorkspaceMemberNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static WorkspaceMemberResponse toMemberResponse(WorkspaceMemberResult result) {
        return new WorkspaceMemberResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.workspaceId(),
                result.workspaceRole().name(),
                result.membershipStatus());
    }

    private static KnowledgeBaseGrantResponse toKnowledgeBaseGrantResponse(
            KnowledgeBaseGrantResult result) {
        return new KnowledgeBaseGrantResponse(
                result.workspaceId(),
                result.kbId(),
                result.userId(),
                result.username(),
                result.displayName(),
                result.role().name(),
                result.status());
    }

    private static DocumentGrantResponse toDocumentGrantResponse(DocumentGrantResult result) {
        return new DocumentGrantResponse(
                result.workspaceId(),
                result.documentId(),
                result.userId(),
                result.username(),
                result.displayName(),
                result.permission().name(),
                result.status());
    }
}
