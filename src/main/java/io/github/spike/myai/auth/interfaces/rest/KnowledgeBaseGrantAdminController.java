package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.RevokeKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.command.UpsertKnowledgeBaseGrantCommand;
import io.github.spike.myai.auth.application.exception.KnowledgeBaseGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ListKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.KnowledgeBaseGrantResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.UpsertKnowledgeBaseGrantRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识库授权治理 REST 控制器。
 * <p>
 * 提供知识库级别授权管理的 HTTP 接口，包括：
 * <ul>
 *   <li>查询指定知识库下所有活跃授权记录</li>
 *   <li>向指定用户授予或更新知识库访问权限（Upsert）</li>
 *   <li>回收指定用户的知识库访问权限（软删除）</li>
 * </ul>
 * 所有接口均要求调用方具备工作区管理权限，权限校验由下游用例层完成。
 * <p>
 * 路径中的 {@code kbId} 为知识库唯一标识，作为所有操作的作用域限定。
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge-bases/{kbId}/grants")
public class KnowledgeBaseGrantAdminController {

    /** 查询知识库授权列表用例 */
    private final ListKnowledgeBaseGrantsUseCase listKnowledgeBaseGrantsUseCase;
    /** 授予或更新知识库授权用例 */
    private final UpsertKnowledgeBaseGrantUseCase upsertKnowledgeBaseGrantUseCase;
    /** 回收知识库授权用例 */
    private final RevokeKnowledgeBaseGrantUseCase revokeKnowledgeBaseGrantUseCase;

    /**
     * 构造器注入所需用例。
     *
     * @param listKnowledgeBaseGrantsUseCase   查询授权列表用例
     * @param upsertKnowledgeBaseGrantUseCase  授予/更新授权用例
     * @param revokeKnowledgeBaseGrantUseCase  回收授权用例
     */
    public KnowledgeBaseGrantAdminController(
            ListKnowledgeBaseGrantsUseCase listKnowledgeBaseGrantsUseCase,
            UpsertKnowledgeBaseGrantUseCase upsertKnowledgeBaseGrantUseCase,
            RevokeKnowledgeBaseGrantUseCase revokeKnowledgeBaseGrantUseCase) {
        this.listKnowledgeBaseGrantsUseCase = listKnowledgeBaseGrantsUseCase;
        this.upsertKnowledgeBaseGrantUseCase = upsertKnowledgeBaseGrantUseCase;
        this.revokeKnowledgeBaseGrantUseCase = revokeKnowledgeBaseGrantUseCase;
    }

    /**
     * 查询指定知识库下所有活跃授权记录。
     * <p>
     * 异常映射：
     * <ul>
     *   <li>知识库不存在 → 404</li>
     *   <li>{@code kbId} 为空或纯空白 → 400</li>
     * </ul>
     *
     * @param kbId 知识库唯一标识
     * @return 授权响应列表，无授权时返回空列表
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnowledgeBaseGrantResponse> listKnowledgeBaseGrants(@PathVariable("kbId") String kbId) {
        try {
            // 委托用例层查询，将结果映射为响应 DTO
            return listKnowledgeBaseGrantsUseCase.handle(kbId).stream()
                    .map(KnowledgeBaseGrantAdminController::toResponse)
                    .toList();
        } catch (ManagedKnowledgeBaseNotFoundException ex) {
            // 知识库不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // kbId 参数非法，映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 授予或更新指定用户对某知识库的访问权限。
     * <p>
     * 采用 Upsert 语义：已存在则更新角色，不存在则新建授权。
     * 异常映射：
     * <ul>
     *   <li>知识库不存在或用户非工作区成员 → 404</li>
     *   <li>请求体为空或角色参数非法 → 400</li>
     * </ul>
     *
     * @param kbId    知识库唯一标识
     * @param userId  目标用户唯一标识
     * @param request 授权请求体，包含目标角色
     * @return 操作后的授权信息
     */
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeBaseGrantResponse upsertKnowledgeBaseGrant(
            @PathVariable("kbId") String kbId,
            @PathVariable("userId") String userId,
            @RequestBody(required = false) UpsertKnowledgeBaseGrantRequest request) {
        // 请求体为空时直接拒绝，避免 NPE
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 构造命令对象并委托用例层执行 Upsert 操作
            return toResponse(upsertKnowledgeBaseGrantUseCase.handle(
                    new UpsertKnowledgeBaseGrantCommand(kbId, userId, request.role())));
        } catch (ManagedKnowledgeBaseNotFoundException | WorkspaceMemberNotFoundException ex) {
            // 知识库或目标用户不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数非法（如角色值无效），映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 回收指定用户对某知识库的访问权限。
     * <p>
     * 软删除操作：将授权状态变更为 DISABLED，保留审计记录。
     * 成功时返回 HTTP 204 No Content。
     * 异常映射：
     * <ul>
     *   <li>知识库不存在或授权记录不存在 → 404</li>
     *   <li>参数非法 → 400</li>
     * </ul>
     *
     * @param kbId   知识库唯一标识
     * @param userId 目标用户唯一标识
     * @return HTTP 204 空响应体
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revokeKnowledgeBaseGrant(
            @PathVariable("kbId") String kbId,
            @PathVariable("userId") String userId) {
        try {
            // 委托用例层执行回收操作
            revokeKnowledgeBaseGrantUseCase.handle(new RevokeKnowledgeBaseGrantCommand(kbId, userId));
            // 回收成功返回 204 No Content
            return ResponseEntity.noContent().build();
        } catch (ManagedKnowledgeBaseNotFoundException | KnowledgeBaseGrantNotFoundException ex) {
            // 知识库或授权记录不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数非法，映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将用例层结果转换为 REST 响应 DTO。
     * <p>
     * 注意：{@code role} 字段从枚举转换为 {@code name()} 字符串，
     * 确保 JSON 输出为可读文本而非枚举常量名。
     *
     * @param result 用例层返回的授权结果
     * @return REST 响应对象
     */
    private static KnowledgeBaseGrantResponse toResponse(KnowledgeBaseGrantResult result) {
        return new KnowledgeBaseGrantResponse(
                result.workspaceId(),
                result.kbId(),
                result.userId(),
                result.username(),
                result.displayName(),
                // 枚举转字符串，确保 JSON 可读性
                result.role().name(),
                result.status());
    }
}
