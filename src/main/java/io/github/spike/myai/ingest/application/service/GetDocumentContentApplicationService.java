package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.query.GetDocumentContentQuery;
import io.github.spike.myai.ingest.application.result.DocumentContentResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentContentUseCase;
import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.github.spike.myai.shared.rest.BusinessException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 文档 latest 正文读取应用服务。
 *
 * <p>该服务固定读取目标 document 当前 latest version 的 {@code cleaned.md}，
 * 用于文档详情默认正文视图。latest 未生成正文时返回 {@code CONTENT_NOT_READY}，
 * 不自动回退到旧版本。
 */
@Service
public class GetDocumentContentApplicationService implements GetDocumentContentUseCase {

    /** 正文来源：当前最新版本。 */
    static final String SOURCE_LATEST = "LATEST";

    /** 文档仓储，用于读取 document latest projection 与 version fact */
    private final DocumentRepository documentRepository;
    /** 版本处理产物存储，用于读取 cleaned.md */
    private final DocumentProcessingArtifactStorage artifactStorage;
    /** 当前用户提供器 */
    private final CurrentUserProvider currentUserProvider;
    /** 授权服务 */
    private final AuthorizationService authorizationService;
    /** 服务端正文读取最大字节数 */
    private final long maxReadBytes;

    /**
     * 构造器注入。
     *
     * @param documentRepository   文档仓储
     * @param artifactStorage      版本处理产物存储
     * @param currentUserProvider  当前用户提供器
     * @param authorizationService 授权服务
     * @param ingestProperties     ingest 配置属性
     */
    public GetDocumentContentApplicationService(
            DocumentRepository documentRepository,
            DocumentProcessingArtifactStorage artifactStorage,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService,
            IngestProperties ingestProperties) {
        this.documentRepository = documentRepository;
        this.artifactStorage = artifactStorage;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.maxReadBytes = ingestProperties.getStorage().getArtifacts().getMaxReadBytes();
    }

    /**
     * 读取 document 当前 latest version 的 Markdown 正文。
     *
     * <p>处理顺序：
     * <ol>
     *   <li>定位当前用户工作区内的 document；</li>
     *   <li>拒绝已删除 document，并校验正文读取权限；</li>
     *   <li>读取 latest version fact，确保返回版本上下文字段来自版本事实；</li>
     *   <li>读取版本级 {@code cleaned.md} 并按状态映射缺失或过大分支。</li>
     * </ol>
     *
     * @param query 正文读取查询
     * @return latest 正文结果
     */
    @Override
    public DocumentContentResult handle(GetDocumentContentQuery query) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        DocumentId documentId = new DocumentId(query.documentId());
        Document document = documentRepository.findById(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> business(
                        HttpStatus.NOT_FOUND,
                        "DOCUMENT_NOT_FOUND",
                        "document not found: " + documentId.value()));

        if (document.status() == UploadStatus.DELETED) {
            throw business(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "document not found: " + documentId.value());
        }
        requireReadPermission(currentUser, document);

        int latestVersionNumber = document.latestVersionNumber();
        DocumentVersion latestVersion = documentRepository.findVersionByNumber(
                        currentUser.workspaceId(),
                        documentId,
                        latestVersionNumber)
                .orElseThrow(() -> business(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "CONTENT_ARTIFACT_MISSING",
                        "latest version fact is missing: " + latestVersionNumber));

        DocumentVersionArtifactContent content = loadLatestContent(currentUser, documentId, latestVersion);
        int askableVersionNumber = documentRepository.findLatestIndexedVersionNumber(currentUser.workspaceId(), documentId);

        return new DocumentContentResult(
                documentId.value(),
                latestVersion.versionNumber(),
                latestVersionNumber,
                true,
                askableVersionNumber == latestVersion.versionNumber(),
                SOURCE_LATEST,
                latestVersion.status().name(),
                latestVersion.filename(),
                latestVersion.createdAt(),
                latestVersion.updatedAt(),
                content.content(),
                content.contentLength(),
                false);
    }

    /**
     * 读取 latest version 的 {@code cleaned.md} 并映射稳定业务错误。
     *
     * @param currentUser   当前用户
     * @param documentId    文档资产 ID
     * @param latestVersion latest version fact
     * @return 正文 artifact 内容
     */
    private DocumentVersionArtifactContent loadLatestContent(
            CurrentUser currentUser,
            DocumentId documentId,
            DocumentVersion latestVersion) {
        try {
            Optional<DocumentVersionArtifactContent> content = artifactStorage.loadVersionArtifact(
                    currentUser.workspaceId(),
                    documentId,
                    latestVersion.versionNumber(),
                    DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                    maxReadBytes);
            if (content.isPresent()) {
                return content.get();
            }
            throw missingContent(latestVersion);
        } catch (DocumentVersionArtifactTooLargeException ex) {
            throw business(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CONTENT_TOO_LARGE",
                    "正文超过服务端读取上限，请联系管理员调整或拆分文档");
        }
    }

    /**
     * 根据 latest 状态区分“尚未生成正文”和“产物异常缺失”。
     *
     * @param version latest version fact
     * @return 业务异常
     */
    private static BusinessException missingContent(DocumentVersion version) {
        if (version.status() == UploadStatus.UPLOADED || version.status() == UploadStatus.INGESTING) {
            return business(HttpStatus.CONFLICT, "CONTENT_NOT_READY", "文档正文仍在生成中，请稍后重试");
        }
        return business(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_ARTIFACT_MISSING", "文档正文产物缺失，请联系管理员修复");
    }

    /**
     * 校验正文读取权限并映射为稳定业务错误码。
     *
     * @param currentUser 当前用户
     * @param document    目标文档
     */
    private void requireReadPermission(CurrentUser currentUser, Document document) {
        try {
            authorizationService.requireCanReadDocument(
                    currentUser,
                    document.documentId().value(),
                    document.kbId());
        } catch (AccessDeniedException ex) {
            throw business(
                    HttpStatus.FORBIDDEN,
                    "DOCUMENT_CONTENT_FORBIDDEN",
                    "你没有读取该文档正文的权限，请联系管理员");
        }
    }

    /**
     * 构造业务异常。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 错误消息
     * @return 业务异常
     */
    private static BusinessException business(HttpStatus status, String code, String message) {
        return new BusinessException(status, code, message);
    }
}
