package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.RevokeDocumentGrantCommand;
import io.github.spike.myai.auth.application.command.UpsertDocumentGrantCommand;
import io.github.spike.myai.auth.application.exception.DocumentGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ListDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeDocumentGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertDocumentGrantUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.DocumentGrantResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.UpsertDocumentGrantRequest;
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
import java.util.List;

/**
 * 文档授权治理 REST 控制器。
 * <p>
 * 提供文档级授权管理的 HTTP 接口，包括：
 * <ul>
 *   <li>查询指定文档下所有活跃授权记录</li>
 *   <li>向指定用户授予或更新文档访问权限覆盖（Upsert）</li>
 *   <li>回收指定用户的文档访问权限覆盖（软删除）</li>
 * </ul>
 * 所有接口均要求调用方具备工作区管理权限，权限校验由下游用例层完成。
 * 治理边界由 {@link io.github.spike.myai.auth.application.service.WorkspaceGovernanceGuard} 守护。
 * <p>
 * 路径中的 {@code documentId} 为文档唯一标识，作为所有操作的作用域限定。
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/documents/{documentId}/grants")
public class DocumentGrantAdminController {

    /** 查询文档授权列表用例 */
    private final ListDocumentGrantsUseCase listDocumentGrantsUseCase;
    /** 授予或更新文档授权用例 */
    private final UpsertDocumentGrantUseCase upsertDocumentGrantUseCase;
    /** 回收文档授权用例 */
    private final RevokeDocumentGrantUseCase revokeDocumentGrantUseCase;

    /**
     * 构造器注入所需用例。
     *
     * @param listDocumentGrantsUseCase          查询授权列表用例
     * @param upsertDocumentGrantUseCase         授予/更新授权用例
     * @param revokeDocumentGrantUseCase         回收授权用例
     */
    public DocumentGrantAdminController(
            ListDocumentGrantsUseCase listDocumentGrantsUseCase,
            UpsertDocumentGrantUseCase upsertDocumentGrantUseCase,
            RevokeDocumentGrantUseCase revokeDocumentGrantUseCase) {
        this.listDocumentGrantsUseCase = listDocumentGrantsUseCase;
        this.upsertDocumentGrantUseCase = upsertDocumentGrantUseCase;
        this.revokeDocumentGrantUseCase = revokeDocumentGrantUseCase;
    }

    /**
     * 查询指定文档下所有活跃授权记录。
     * <p>
     * 异常映射：
     * <ul>
     *   <li>文档不存在 → 404</li>
     *   <li>{@code documentId} 为空或纯空白 → 400</li>
     * </ul>
     *
     * @param documentId 文档唯一标识
     * @return 授权响应列表，无授权时返回空列表
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DocumentGrantResponse> listDocumentGrants(@PathVariable("documentId") String documentId) {
        try {
            // 委托用例层查询，将结果映射为响应 DTO
            return listDocumentGrantsUseCase.handle(documentId).stream()
                    .map(DocumentGrantAdminController::toResponse)
                    .toList();
        } catch (ManagedDocumentNotFoundException ex) {
            // 文档不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // documentId 参数非法，映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 授予或更新指定用户对某文档的访问权限覆盖。
     * <p>
     * 采用 Upsert 语义：已存在则更新权限，不存在则新建授权。
     * 异常映射：
     * <ul>
     *   <li>文档不存在或用户非工作区成员 → 404</li>
     *   <li>请求体为空或权限参数非法 → 400</li>
     * </ul>
     *
     * @param documentId 文档唯一标识
     * @param userId     目标用户唯一标识
     * @param request    授权请求体，包含目标权限
     * @return 操作后的授权信息
     */
    @PutMapping(value = "/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentGrantResponse upsertDocumentGrant(
            @PathVariable("documentId") String documentId,
            @PathVariable("userId") String userId,
            @RequestBody(required = false) UpsertDocumentGrantRequest request) {
        // 请求体为空时直接拒绝，避免 NPE
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 构造命令对象并委托用例层执行 Upsert 操作
            return toResponse(upsertDocumentGrantUseCase.handle(
                    new UpsertDocumentGrantCommand(documentId, userId, request.permission())));
        } catch (ManagedDocumentNotFoundException | WorkspaceMemberNotFoundException ex) {
            // 文档或目标用户不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数非法（如权限值无效），映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 回收指定用户对某文档的访问权限覆盖。
     * <p>
     * 软删除操作：将授权状态变更为 DISABLED，保留审计记录。
     * 成功时返回 HTTP 204 No Content。
     * 异常映射：
     * <ul>
     *   <li>文档不存在或授权记录不存在 → 404</li>
     *   <li>参数非法 → 400</li>
     * </ul>
     *
     * @param documentId 文档唯一标识
     * @param userId     目标用户唯一标识
     * @return HTTP 204 空响应体
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revokeDocumentGrant(
            @PathVariable("documentId") String documentId,
            @PathVariable("userId") String userId) {
        try {
            // 委托用例层执行回收操作
            revokeDocumentGrantUseCase.handle(new RevokeDocumentGrantCommand(documentId, userId));
            // 回收成功返回 204 No Content
            return ResponseEntity.noContent().build();
        } catch (ManagedDocumentNotFoundException | DocumentGrantNotFoundException ex) {
            // 文档或授权记录不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数非法，映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将用例层结果转换为 REST 响应 DTO。
     * <p>
     * 注意：{@code permission} 字段从枚举转换为 {@code name()} 字符串，
     * 确保 JSON 输出为可读文本而非枚举常量名。
     *
     * @param result 用例层返回的授权结果
     * @return REST 响应对象
     */
    private static DocumentGrantResponse toResponse(DocumentGrantResult result) {
        return new DocumentGrantResponse(
                result.workspaceId(),
                result.documentId(),
                result.userId(),
                result.username(),
                result.displayName(),
                // 枚举转字符串，确保 JSON 可读性
                result.permission().name(),
                result.status());
    }
}
