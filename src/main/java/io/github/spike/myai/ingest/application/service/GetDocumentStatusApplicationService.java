package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import org.springframework.stereotype.Service;

/**
 * 查询文档状态应用服务（Application Service）。
 *
 * <p>该服务实现 {@link GetDocumentStatusUseCase} 用例接口，
 * 提供轻量级的文档处理状态查询能力。典型使用场景为：
 * 前端在上传文档后定时轮询此接口，获取文档从 UPLOADED →
 * INGESTING → INDEXED（或 FAILED）的状态变迁。
 *
 * <p>职责：
 * <ol>
 *   <li>接收查询参数并构造领域值对象 {@link DocumentId}；</li>
 *   <li>通过仓储端口查询文档聚合根；</li>
 *   <li>校验当前用户对该文档的读取权限；</li>
 *   <li>将领域对象映射为轻量级返回模型（含 documentId + status + processingMetadata）。</li>
 * </ol>
 *
 * <p>设计说明：该服务为只读操作，不涉及事务管理。
 * 返回模型仅包含状态信息与终态处理元数据，不暴露文档内容或分块数据。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class GetDocumentStatusApplicationService implements GetDocumentStatusUseCase {

    /** 文档仓储端口：用于按工作区+文档ID查询文档聚合根 */
    private final DocumentRepository documentRepository;

    /** 当前用户上下文提供器：用于获取工作区标识和认证状态 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权服务：用于校验当前用户是否可读取该文档 */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * @param documentRepository   文档仓储（领域端口）
     * @param currentUserProvider  当前用户上下文提供器（应用层端口）
     * @param authorizationService 授权服务（应用层）
     */
    public GetDocumentStatusApplicationService(
            DocumentRepository documentRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询文档当前处理状态。
     *
     * <p>用于前端轮询文档入库进度（如是否已完成向量化等）。
     * 处理流程：
     * <ol>
     *   <li>从安全上下文获取当前用户及工作区；</li>
     *   <li>将字符串文档 ID 转换为领域值对象；</li>
     *   <li>按工作区 + 文档 ID 查询文档（不存在则抛出异常）；</li>
     *   <li>校验读取权限（三级判定），权不足则抛出异常；</li>
     *   <li>返回轻量级状态结果。</li>
     * </ol>
     *
     * @param query 查询参数（含文档 ID 字符串）
     * @return 文档状态结果（含 documentId 与 status）
     * @throws DocumentNotFoundException 当文档不存在时
     * @throws org.springframework.security.access.AccessDeniedException 权限不足时（由授权服务抛出）
     */
    @Override
    public DocumentStatusResult handle(GetDocumentStatusQuery query) {
        // 获取当前登录用户及工作区标识
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        // 将字符串 ID 转换为类型安全的领域值对象
        DocumentId documentId = new DocumentId(query.documentId());
        // 按工作区 + 文档 ID 查询聚合根，不存在则抛出 DocumentNotFoundException
        Document document = documentRepository.findById(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        // 权限校验：三级判定（工作区 → 文档 → 知识库回退），权不足时抛出 AccessDeniedException
        authorizationService.requireCanReadDocument(currentUser, documentId.value(), document.kbId());
        // processing_metadata 只在终态暴露，避免处理中状态出现半成品视图。
        return new DocumentStatusResult(
                document.documentId(),
                document.kbId(),
                document.latestVersionNumber(),
                document.filename(),
                document.latestVersionOriginType(),
                document.status(),
                shouldExposeProcessingMetadata(document.status()) ? document.processingMetadata() : null);
    }

    /**
     * 判断当前文档状态是否允许对外暴露 processing_metadata。
     *
     * <p>设计原则：仅终态（{@link UploadStatus#INDEXED} 或 {@link UploadStatus#FAILED}）
     * 允许透传处理结果元数据，避免处理中状态（UPLOADED / INGESTING）
     * 出现半成品视图，确保前端展示的数据是完整的。
     *
     * @param status 文档当前处理状态
     * @return 是否允许暴露 processing_metadata
     */
    private static boolean shouldExposeProcessingMetadata(UploadStatus status) {
        return status == UploadStatus.INDEXED || status == UploadStatus.FAILED;
    }
}
