package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.ListWorkspaceMembersUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateWorkspaceMemberRoleUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.UpdateWorkspaceMemberRoleRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.WorkspaceMemberResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 工作区成员治理 REST 控制器。
 * <p>
 * 提供工作区成员管理的 HTTP 接口，包括：
 * <ul>
 *   <li>查询当前工作区所有活跃成员列表</li>
 *   <li>更新指定成员的工作区角色</li>
 * </ul>
 * 所有接口均要求调用方具备工作区管理权限，权限校验由下游用例层完成。
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/members")
public class WorkspaceMemberAdminController {

    /** 查询工作区成员列表用例 */
    private final ListWorkspaceMembersUseCase listWorkspaceMembersUseCase;
    /** 更新工作区成员角色用例 */
    private final UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase;

    /**
     * 构造器注入所需用例。
     *
     * @param listWorkspaceMembersUseCase   查询成员列表用例
     * @param updateWorkspaceMemberRoleUseCase 更新成员角色用例
     */
    public WorkspaceMemberAdminController(
            ListWorkspaceMembersUseCase listWorkspaceMembersUseCase,
            UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase) {
        this.listWorkspaceMembersUseCase = listWorkspaceMembersUseCase;
        this.updateWorkspaceMemberRoleUseCase = updateWorkspaceMemberRoleUseCase;
    }

    /**
     * 查询当前工作区所有活跃成员。
     * <p>
     * 调用用例层获取当前用户所属工作区的活跃成员列表，
     * 并将领域结果转换为 REST 响应 DTO 后返回。
     *
     * @return 工作区成员响应列表，若无成员则返回空列表
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<WorkspaceMemberResponse> listMembers() {
        // 调用用例层获取成员列表，并通过 Stream 映射为响应 DTO
        return listWorkspaceMembersUseCase.handle().stream()
                .map(WorkspaceMemberAdminController::toResponse)
                .toList();
    }

    /**
     * 更新指定成员的工作区角色。
     * <p>
     * 接收路径参数中的用户 ID 和请求体中的目标角色，
     * 构造命令对象后交由用例层执行角色变更。
     * 对业务异常进行统一转换为 HTTP 标准状态码：
     * <ul>
     *   <li>成员不存在 → 404</li>
     *   <li>参数非法（如无效角色值）→ 400</li>
     * </ul>
     *
     * @param userId  目标用户的唯一标识
     * @param request 角色更新请求体，包含目标工作区角色
     * @return 更新后的工作区成员信息
     * @throws ResponseStatusException 当请求体为空、成员不存在或参数非法时抛出
     */
    @PatchMapping(value = "/{userId}/role", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkspaceMemberResponse updateMemberRole(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) UpdateWorkspaceMemberRoleRequest request) {
        // 请求体为空时直接拒绝，避免 NPE
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 构造命令对象并委托用例层执行角色更新
            return toResponse(updateWorkspaceMemberRoleUseCase.handle(
                    new UpdateWorkspaceMemberRoleCommand(userId, request.workspaceRole())));
        } catch (WorkspaceMemberNotFoundException ex) {
            // 目标成员不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 角色参数非法（如未知角色枚举值），映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将领域层返回的结果对象转换为 REST 响应 DTO。
     *
     * @param result 用例层返回的工作区成员结果
     * @return 对应的 REST 响应对象
     */
    private static WorkspaceMemberResponse toResponse(WorkspaceMemberResult result) {
        return new WorkspaceMemberResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.workspaceId(),
                result.workspaceRole().name(),
                result.membershipStatus());
    }
}
