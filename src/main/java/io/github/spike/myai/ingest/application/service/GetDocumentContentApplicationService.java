package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.query.DocumentContentSource;
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
 * 文档正文读取应用服务。
 *
 * <p>该服务按 {@link DocumentContentSource} 选择版本级 {@code cleaned.md}：
 * {@code LATEST} 固定读取当前最新版本；{@code ASKABLE_BASELINE} 读取当前 QA
 * 可问答基线版本；{@code EXPLICIT_VERSION} 读取调用方指定的历史版本。
 * 正文读取只读版本事实和 artifact，不改变后续问答基线。
 */
@Service
public class GetDocumentContentApplicationService implements GetDocumentContentUseCase {

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
     * 按来源读取 document 的 Markdown 正文。
     *
     * <p>处理顺序：
     * <ol>
     *   <li>定位当前用户工作区内的 document；</li>
     *   <li>拒绝已删除 document，并按来源校验正文读取或历史版本读取权限；</li>
     *   <li>根据来源选择 latest、askable baseline 或显式指定版本；</li>
     *   <li>读取版本级 {@code cleaned.md} 并按来源映射缺失或过大分支。</li>
     * </ol>
     *
     * @param query 正文读取查询
     * @return 正文结果
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
        requireContentPermission(currentUser, document, query.source());

        int latestVersionNumber = document.latestVersionNumber();
        int askableVersionNumber = documentRepository.findLatestIndexedVersionNumber(currentUser.workspaceId(), documentId);
        DocumentVersion selectedVersion = selectVersion(
                currentUser,
                documentId,
                query.source(),
                query.versionNumber(),
                latestVersionNumber,
                askableVersionNumber);
        rejectDeletedVersion(selectedVersion);

        DocumentVersionArtifactContent content = loadContent(currentUser, documentId, selectedVersion, query.source());

        return new DocumentContentResult(
                documentId.value(),
                selectedVersion.versionNumber(),
                latestVersionNumber,
                selectedVersion.versionNumber() == latestVersionNumber,
                askableVersionNumber == selectedVersion.versionNumber(),
                query.source().name(),
                selectedVersion.status().name(),
                selectedVersion.filename(),
                selectedVersion.createdAt(),
                selectedVersion.updatedAt(),
                content.content(),
                content.contentLength(),
                false);
    }

    /**
     * 根据正文来源选择目标版本事实。
     *
     * @param currentUser   当前用户
     * @param documentId    文档资产 ID
     * @param source        正文来源
     * @param versionNumber 显式版本读取时的目标版本号
     * @param latestVersionNumber 当前 latest 版本号
     * @param askableVersionNumber 当前可问答版本号；不存在时为 0
     * @return 目标版本事实
     */
    private DocumentVersion selectVersion(
            CurrentUser currentUser,
            DocumentId documentId,
            DocumentContentSource source,
            Integer versionNumber,
            int latestVersionNumber,
            int askableVersionNumber) {
        if (source == DocumentContentSource.ASKABLE_BASELINE) {
            if (askableVersionNumber == 0) {
                throw contentNotReady();
            }
            return findVersionFact(currentUser, documentId, askableVersionNumber, "askable baseline");
        }
        if (source == DocumentContentSource.EXPLICIT_VERSION) {
            return findExplicitVersionFact(currentUser, documentId, versionNumber);
        }
        return findVersionFact(currentUser, documentId, latestVersionNumber, "latest");
    }

    /**
     * 读取显式指定的版本事实，未命中时映射为对外稳定的版本不存在错误。
     *
     * @param currentUser   当前用户
     * @param documentId    文档资产 ID
     * @param versionNumber 目标版本号
     * @return 版本事实
     */
    private DocumentVersion findExplicitVersionFact(
            CurrentUser currentUser,
            DocumentId documentId,
            int versionNumber) {
        return documentRepository.findVersionByNumber(
                        currentUser.workspaceId(),
                        documentId,
                        versionNumber)
                .orElseThrow(() -> business(
                        HttpStatus.NOT_FOUND,
                        "VERSION_NOT_FOUND",
                        "document version not found: " + versionNumber));
    }

    /**
     * 读取版本事实，缺失时映射为稳定业务错误。
     *
     * @param currentUser   当前用户
     * @param documentId    文档资产 ID
     * @param versionNumber 版本号
     * @param sourceLabel   错误信息中的版本来源标签
     * @return 版本事实
     */
    private DocumentVersion findVersionFact(
            CurrentUser currentUser,
            DocumentId documentId,
            int versionNumber,
            String sourceLabel) {
        return documentRepository.findVersionByNumber(
                        currentUser.workspaceId(),
                        documentId,
                        versionNumber)
                .orElseThrow(() -> business(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "CONTENT_ARTIFACT_MISSING",
                        sourceLabel + " version fact is missing: " + versionNumber));
    }

    /**
     * 读取目标 version 的 {@code cleaned.md} 并映射稳定业务错误。
     *
     * @param currentUser 当前用户
     * @param documentId  文档资产 ID
     * @param version     目标 version fact
     * @param source      正文来源
     * @return 正文 artifact 内容
     */
    private DocumentVersionArtifactContent loadContent(
            CurrentUser currentUser,
            DocumentId documentId,
            DocumentVersion version,
            DocumentContentSource source) {
        try {
            Optional<DocumentVersionArtifactContent> content = artifactStorage.loadVersionArtifact(
                    currentUser.workspaceId(),
                    documentId,
                    version.versionNumber(),
                    DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                    maxReadBytes);
            if (content.isPresent()) {
                return content.get();
            }
            throw missingContent(version);
        } catch (DocumentVersionArtifactTooLargeException ex) {
            throw business(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CONTENT_TOO_LARGE",
                    "正文超过服务端读取上限，请联系管理员调整或拆分文档");
        }
    }

    /**
     * 根据版本状态与正文来源区分“尚未生成正文”和“产物异常缺失”。
     *
     * @param version 版本事实
     * @return 业务异常
     */
    private static BusinessException missingContent(DocumentVersion version) {
        if (version.status() == UploadStatus.UPLOADED
                || version.status() == UploadStatus.INGESTING) {
            return contentNotReady();
        }
        return business(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_ARTIFACT_MISSING", "文档正文产物缺失，请联系管理员修复");
    }

    /**
     * 阻止已删除版本继续读取历史正文产物。
     *
     * @param version 版本事实
     */
    private static void rejectDeletedVersion(DocumentVersion version) {
        if (version.status() == UploadStatus.DELETED) {
            throw business(
                    HttpStatus.NOT_FOUND,
                    "VERSION_NOT_FOUND",
                    "document version not found: " + version.versionNumber());
        }
    }

    /**
     * 构造正文未就绪业务异常。
     *
     * @return 业务异常
     */
    private static BusinessException contentNotReady() {
        return business(HttpStatus.CONFLICT, "CONTENT_NOT_READY", "文档正文仍在生成中，请稍后重试");
    }

    /**
     * 按正文来源校验读取权限并映射为稳定业务错误码。
     *
     * @param currentUser 当前用户
     * @param document    目标文档
     * @param source      正文来源
     */
    private void requireContentPermission(CurrentUser currentUser, Document document, DocumentContentSource source) {
        if (source == DocumentContentSource.EXPLICIT_VERSION) {
            requireExplicitVersionPermission(currentUser, document);
            return;
        }
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
     * 校验显式历史版本正文读取权限。
     *
     * @param currentUser 当前用户
     * @param document    目标文档
     */
    private void requireExplicitVersionPermission(CurrentUser currentUser, Document document) {
        try {
            authorizationService.requireCanManageDocument(
                    currentUser,
                    document.documentId().value(),
                    document.kbId());
        } catch (AccessDeniedException ex) {
            throw business(
                    HttpStatus.FORBIDDEN,
                    "VERSION_CONTENT_FORBIDDEN",
                    "你没有读取该历史版本正文的权限，请联系管理员");
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
