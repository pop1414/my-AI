package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ListDocumentGrantsUseCase;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 查询文档授权列表应用服务。
 * <p>
 * 实现 {@link ListDocumentGrantsUseCase} 用例，负责以下职责：
 * <ol>
 *   <li>校验当前用户是否具备工作区管理权限</li>
 *   <li>校验并规范化 {@code documentId} 参数</li>
 *   <li>验证目标文档在当前工作区中存在</li>
 *   <li>查询该文档下所有活跃授权记录并转换为结果对象</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class ListDocumentGrantsApplicationService implements ListDocumentGrantsUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 文档仓储，用于校验文档存在性 */
    private final DocumentRepository documentRepository;
    /** 文档授权治理仓储 */
    private final DocumentGrantManagementRepository grantRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService 授权服务
     * @param documentRepository   文档仓储
     * @param grantRepository      文档授权治理仓储
     */
    public ListDocumentGrantsApplicationService(
            AuthorizationService authorizationService,
            DocumentRepository documentRepository,
            DocumentGrantManagementRepository grantRepository) {
        this.authorizationService = authorizationService;
        this.documentRepository = documentRepository;
        this.grantRepository = grantRepository;
    }

    /**
     * 执行查询文档授权列表用例。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>校验当前用户的工作区管理权限</li>
     *   <li>校验并规范化 {@code documentId}</li>
     *   <li>确认文档存在，不存在则抛出 {@link ManagedDocumentNotFoundException}</li>
     *   <li>查询活跃授权记录并转换为结果列表</li>
     * </ol>
     *
     * @param documentId 文档唯一标识
     * @return 授权结果列表，无授权时返回空列表
     * @throws ManagedDocumentNotFoundException 当文档不存在时抛出
     * @throws IllegalArgumentException         当 {@code documentId} 为空或纯空白时抛出
     */
    @Override
    public List<DocumentGrantResult> handle(String documentId) {
        // Step 1: 校验工作区管理权限，获取当前用户上下文
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // Step 2: 校验并规范化 documentId（非空、trim）
        String normalizedDocumentId = requireDocumentId(documentId);
        // Step 3: 确认文档在当前工作区中存在
        ensureDocumentExists(currentUser.workspaceId(), normalizedDocumentId);
        // Step 4: 查询活跃授权记录并转换为结果列表
        return grantRepository.findActiveGrants(currentUser.workspaceId(), normalizedDocumentId).stream()
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
     * 校验文档在当前工作区中是否存在。
     * <p>
     * 若查询结果为空则抛出 {@link ManagedDocumentNotFoundException}，
     * 由上层控制器映射为 HTTP 404。
     *
     * @param workspaceId 工作区 ID
     * @param documentId  文档 ID
     * @throws ManagedDocumentNotFoundException 当文档不存在时抛出
     */
    private void ensureDocumentExists(String workspaceId, String documentId) {
        if (documentRepository.findById(workspaceId, new DocumentId(documentId)).isEmpty()) {
            throw new ManagedDocumentNotFoundException("document not found: " + documentId);
        }
    }

    /**
     * 校验并规范化文档 ID。
     *
     * @param documentId 原始文档 ID
     * @return trim 后的文档 ID
     * @throws IllegalArgumentException 当值为 {@code null} 或纯空白时抛出
     */
    private static String requireDocumentId(String documentId) {
        if (documentId == null || documentId.trim().isEmpty()) {
            throw new IllegalArgumentException("documentId is required");
        }
        return documentId.trim();
    }
}
