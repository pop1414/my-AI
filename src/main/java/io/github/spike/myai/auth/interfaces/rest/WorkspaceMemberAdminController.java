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

    /**
     * 构造函数注入所有依赖的用例。
     *
     * @param listWorkspaceMembersUseCase              查询工作区成员列表用例
     * @param listMemberKnowledgeBaseGrantsUseCase     查询成员知识库授权列表用例
     * @param replaceMemberKnowledgeBaseGrantsUseCase  批量替换成员知识库授权用例
     * @param listMemberDocumentGrantsUseCase          查询成员文档授权列表用例
     * @param replaceMemberDocumentGrantsUseCase       批量替换成员文档授权用例
     * @param updateWorkspaceMemberRoleUseCase         更新成员工作区角色用例
     */
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

    /**
     * 查询当前工作区所有成员列表。
     *
     * <p>GET /api/v1/admin/members
     *
     * @return 当前工作区的成员列表，包含用户信息、角色和成员状态
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<WorkspaceMemberResponse> listMembers() {
        return listWorkspaceMembersUseCase.handle().stream()
                .map(WorkspaceMemberAdminController::toMemberResponse)
                .toList();
    }

    /**
     * 更新指定成员的工作区角色。
     *
     * <p>PATCH /api/v1/admin/members/{userId}/role
     * <p>仅 WORKSPACE_OWNER 可变更 ADMIN 角色，ADMIN 不可变更同级角色。
     *
     * @param userId  目标成员的用户 ID
     * @param request 含目标工作区角色的请求体
     * @return 更新后的成员信息
     * @throws ResponseStatusException 当成员不存在(404)或参数非法(400)时抛出
     */
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

    /**
     * 查询指定成员的知识库授权列表。
     *
     * <p>GET /api/v1/admin/members/{userId}/knowledge-base-grants
     *
     * @param userId 目标成员的用户 ID
     * @return 该成员在当前工作区的所有知识库授权记录
     * @throws ResponseStatusException 当成员不存在(404)或参数非法(400)时抛出
     */
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

    /**
     * 批量替换指定成员的知识库授权（声明式同步）。
     *
     * <p>PUT /api/v1/admin/members/{userId}/knowledge-base-grants:batch
     * <p>将成员的知识库授权集合整体替换为传入的期望授权列表：
     * <ul>
     *   <li>期望中有的新增或更新；</li>
     *   <li>期望中没有的软删除。</li>
     * </ul>
     *
     * @param userId  目标成员的用户 ID
     * @param request 含期望授权列表的请求体
     * @return 替换后的完整授权列表
     * @throws ResponseStatusException 当成员不存在(404)或参数非法(400)时抛出
     */
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

    /**
     * 查询指定成员的文档授权列表。
     *
     * <p>GET /api/v1/admin/members/{userId}/document-grants
     *
     * @param userId 目标成员的用户 ID
     * @return 该成员在当前工作区的所有文档授权记录
     * @throws ResponseStatusException 当成员不存在(404)或参数非法(400)时抛出
     */
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

    /**
     * 批量替换指定成员的文档授权（声明式同步）。
     *
     * <p>PUT /api/v1/admin/members/{userId}/document-grants:batch
     * <p>将成员的文档授权集合整体替换为传入的期望授权列表。
     *
     * @param userId  目标成员的用户 ID
     * @param request 含期望授权列表的请求体
     * @return 替换后的完整授权列表
     * @throws ResponseStatusException 当成员不存在(404)或参数非法(400)时抛出
     */
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

    /**
     * 将用例层成员结果转换为 REST 响应 DTO。
     *
     * @param result 用例层成员结果
     * @return REST 成员响应 DTO
     */
    private static WorkspaceMemberResponse toMemberResponse(WorkspaceMemberResult result) {
        return new WorkspaceMemberResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.workspaceId(),
                result.workspaceRole().name(),
                result.membershipStatus());
    }

    /**
     * 将用例层知识库授权结果转换为 REST 响应 DTO。
     *
     * @param result 用例层知识库授权结果
     * @return REST 知识库授权响应 DTO
     */
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

    /**
     * 将用例层文档授权结果转换为 REST 响应 DTO。
     *
     * @param result 用例层文档授权结果
     * @return REST 文档授权响应 DTO
     */
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
