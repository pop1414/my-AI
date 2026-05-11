package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.CreateManagedAccountCommand;
import io.github.spike.myai.auth.application.command.RemoveManagedAccountMembershipCommand;
import io.github.spike.myai.auth.application.command.ResetManagedAccountPasswordCommand;
import io.github.spike.myai.auth.application.command.UpdateManagedAccountStatusCommand;
import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.CreateManagedAccountUseCase;
import io.github.spike.myai.auth.application.usecase.ListManagedAccountsUseCase;
import io.github.spike.myai.auth.application.usecase.RemoveManagedAccountMembershipUseCase;
import io.github.spike.myai.auth.application.usecase.ResetManagedAccountPasswordUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateManagedAccountStatusUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.CreateManagedAccountRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.ManagedAccountResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.ResetManagedAccountPasswordRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.UpdateManagedAccountStatusRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 账号管理 REST 控制器。
 *
 * <p>提供工作区管理员对托管账号的 CRUD 操作接口，包括账号列表查询、
 * 创建账号、更新状态、重置密码和移除成员关系。所有接口均需要
 * {@code WORKSPACE_ADMIN} 角色权限，由用例层的授权服务统一校验。
 *
 * <p>异常映射规则：
 * <ul>
 *   <li>{@link ManagedAccountNotFoundException} → HTTP 404</li>
 *   <li>{@link ManagedAccountUsernameConflictException} → HTTP 409</li>
 *   <li>{@link IllegalArgumentException} → HTTP 400</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AccountAdminController {

    private final ListManagedAccountsUseCase listManagedAccountsUseCase;
    private final CreateManagedAccountUseCase createManagedAccountUseCase;
    private final UpdateManagedAccountStatusUseCase updateManagedAccountStatusUseCase;
    private final ResetManagedAccountPasswordUseCase resetManagedAccountPasswordUseCase;
    private final RemoveManagedAccountMembershipUseCase removeManagedAccountMembershipUseCase;

    /**
     * 构造函数注入所有依赖的用例。
     *
     * @param listManagedAccountsUseCase            查询账号列表用例
     * @param createManagedAccountUseCase           创建账号用例
     * @param updateManagedAccountStatusUseCase     更新账号状态用例
     * @param resetManagedAccountPasswordUseCase    重置密码用例
     * @param removeManagedAccountMembershipUseCase 移除成员关系用例
     */
    public AccountAdminController(
            ListManagedAccountsUseCase listManagedAccountsUseCase,
            CreateManagedAccountUseCase createManagedAccountUseCase,
            UpdateManagedAccountStatusUseCase updateManagedAccountStatusUseCase,
            ResetManagedAccountPasswordUseCase resetManagedAccountPasswordUseCase,
            RemoveManagedAccountMembershipUseCase removeManagedAccountMembershipUseCase) {
        this.listManagedAccountsUseCase = listManagedAccountsUseCase;
        this.createManagedAccountUseCase = createManagedAccountUseCase;
        this.updateManagedAccountStatusUseCase = updateManagedAccountStatusUseCase;
        this.resetManagedAccountPasswordUseCase = resetManagedAccountPasswordUseCase;
        this.removeManagedAccountMembershipUseCase = removeManagedAccountMembershipUseCase;
    }

    /**
     * 查询当前工作区所有托管账号。
     *
     * <p>GET /api/v1/admin/accounts
     *
     * @return 账号列表，包含用户信息、成员状态和锁定状态
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ManagedAccountResponse> listAccounts() {
        // 委托用例层查询，将领域结果转换为响应 DTO
        return listManagedAccountsUseCase.handle().stream()
                .map(AccountAdminController::toResponse)
                .toList();
    }

    /**
     * 创建托管账号。
     *
     * <p>POST /api/v1/admin/accounts
     *
     * @param request 创建账号请求体，包含用户名、展示名称、密码和工作区角色
     * @return 创建成功的账号信息
     * @throws ResponseStatusException 如果请求体为空（400）、参数非法（400）或用户名冲突（409）
     */
    @PostMapping(value = {"", "/"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ManagedAccountResponse createAccount(@RequestBody(required = false) CreateManagedAccountRequest request) {
        // 校验请求体不能为空
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将请求 DTO 转换为命令对象，委托用例层执行
            return toResponse(createManagedAccountUseCase.handle(new CreateManagedAccountCommand(
                    request.username(),
                    request.displayName(),
                    request.password(),
                    request.workspaceRole())));
        } catch (ManagedAccountUsernameConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 更新账号状态。
     *
     * <p>PATCH /api/v1/admin/accounts/{userId}/status
     *
     * @param userId  路径参数，目标用户 ID
     * @param request 请求体，包含目标状态（ACTIVE 或 DISABLED）
     * @return 更新后的账号信息
     * @throws ResponseStatusException 如果请求体为空（400）、参数非法（400）或账号不存在（404）
     */
    @PatchMapping(value = "/{userId}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ManagedAccountResponse updateStatus(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) UpdateManagedAccountStatusRequest request) {
        // 校验请求体不能为空
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将路径参数和请求体组装为命令对象，委托用例层执行
            return toResponse(updateManagedAccountStatusUseCase.handle(
                    new UpdateManagedAccountStatusCommand(userId, request.userStatus())));
        } catch (ManagedAccountNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 重置账号密码。
     *
     * <p>POST /api/v1/admin/accounts/{userId}/password/reset
     *
     * @param userId  路径参数，目标用户 ID
     * @param request 请求体，包含新密码
     * @return 重置密码后的账号信息（含清零的锁定状态）
     * @throws ResponseStatusException 如果请求体为空（400）、参数非法（400）或账号不存在（404）
     */
    @PostMapping(value = "/{userId}/password/reset", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ManagedAccountResponse resetPassword(
            @PathVariable("userId") String userId,
            @RequestBody(required = false) ResetManagedAccountPasswordRequest request) {
        // 校验请求体不能为空
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将路径参数和请求体组装为命令对象，委托用例层执行
            return toResponse(resetManagedAccountPasswordUseCase.handle(
                    new ResetManagedAccountPasswordCommand(userId, request.password())));
        } catch (ManagedAccountNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 移除成员关系。
     *
     * <p>DELETE /api/v1/admin/accounts/{userId}/membership
     *
     * @param userId 路径参数，目标用户 ID
     * @return HTTP 204 No Content（成功移除或已是 INACTIVE 状态）
     * @throws ResponseStatusException 如果参数非法（400）或账号不存在（404）
     */
    @DeleteMapping("/{userId}/membership")
    public ResponseEntity<Void> removeMembership(@PathVariable("userId") String userId) {
        try {
            // 将路径参数组装为命令对象，委托用例层执行
            removeManagedAccountMembershipUseCase.handle(new RemoveManagedAccountMembershipCommand(userId));
            // 成功移除或幂等返回，统一返回 204
            return ResponseEntity.noContent().build();
        } catch (ManagedAccountNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将用例层结果转换为 HTTP 响应 DTO。
     *
     * @param result 用例层返回的账号结果
     * @return HTTP 响应 DTO
     */
    private static ManagedAccountResponse toResponse(ManagedAccountResult result) {
        return new ManagedAccountResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.userStatus(),
                result.workspaceId(),
                result.workspaceRole().name(),
                result.membershipStatus(),
                result.failedLoginCount(),
                result.lockedUntil());
    }
}
