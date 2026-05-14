package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.command.RollbackDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionRollbackResult;
import io.github.spike.myai.ingest.application.usecase.RollbackDocumentVersionUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.shared.rest.BusinessException;
import io.github.spike.myai.shared.rest.GlobalRestExceptionHandler;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 文档版本回退应用服务。
 *
 * <p>回退操作保持版本链线性：不会回拨 latest 指针到旧版本，而是复制目标历史版本的文件事实，
 * 创建一个 {@link DocumentVersionOriginType#ROLLBACK} 来源的新最新版本，并将其状态置为
 * {@link UploadStatus#UPLOADED} 重新进入处理链路。
 *
 * <p>所有业务校验失败均通过 {@link BusinessException} 抛出，
 * 由 {@link GlobalRestExceptionHandler} 统一转换为结构化 JSON 错误响应。
 */
@Service
public class RollbackDocumentVersionApplicationService implements RollbackDocumentVersionUseCase {

    /** 文档元数据仓储 */
    private final DocumentRepository documentRepository;
    /** 源文件存储 */
    private final DocumentSourceStorage documentSourceStorage;
    /** 当前用户提供者 */
    private final CurrentUserProvider currentUserProvider;
    /** 权限校验服务 */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * @param documentRepository    文档元数据仓储
     * @param documentSourceStorage 源文件存储
     * @param currentUserProvider   当前用户提供者
     * @param authorizationService  权限校验服务
     */
    public RollbackDocumentVersionApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 执行版本回退。
     *
     * <p>处理顺序：
     * <ol>
     *   <li>查询 document latest projection 并校验管理权限；</li>
     *   <li>校验 expectedLatestVersionNumber，防止过期页面提交；</li>
     *   <li>校验目标版本存在、不是当前 latest，且状态为 INDEXED；</li>
     *   <li>复制目标版本源文件到新版本路径；</li>
     *   <li>CAS 追加 ROLLBACK 来源的新 latest 版本。</li>
     * </ol>
     *
     * @param command 版本回退命令
     * @return 版本回退结果
     */
    @Override
    public DocumentVersionRollbackResult handle(RollbackDocumentVersionCommand command) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        DocumentId documentId = new DocumentId(command.documentId());

        Document document = documentRepository.findById(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> business(
                        HttpStatus.NOT_FOUND,
                        "VERSION_ROLLBACK_DOCUMENT_NOT_FOUND",
                        "document not found: " + documentId.value()));

        requireManagePermission(currentUser, document);

        int latestVersionNumber = document.latestVersionNumber();
        if (command.expectedLatestVersionNumber() != latestVersionNumber) {
            throw staleLatestVersion();
        }
        if (document.status() != UploadStatus.INDEXED && document.status() != UploadStatus.FAILED) {
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_ROLLBACK_NOT_ALLOWED_STATUS",
                    "版本回退仅允许在当前最新版本为 INDEXED 或 FAILED 时发起");
        }
        if (command.targetVersionNumber() == latestVersionNumber) {
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_ROLLBACK_TARGET_IS_LATEST",
                    "当前最新版本不能作为回退目标");
        }

        DocumentVersion targetVersion = documentRepository.findVersionByNumber(
                        currentUser.workspaceId(),
                        documentId,
                        command.targetVersionNumber())
                .orElseThrow(() -> business(
                        HttpStatus.NOT_FOUND,
                        "VERSION_ROLLBACK_VERSION_NOT_FOUND",
                        "document version not found: " + command.targetVersionNumber()));

        if (targetVersion.status() != UploadStatus.INDEXED) {
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_ROLLBACK_TARGET_NOT_INDEXED",
                    "版本回退只允许选择已形成可用内容的历史版本");
        }

        int newVersionNumber = latestVersionNumber + 1;
        Instant now = Instant.now();
        DocumentVersion rollbackVersion = buildRollbackVersion(documentId, newVersionNumber, targetVersion, now);

        byte[] sourceContent = documentSourceStorage
                .loadVersion(documentId, targetVersion.versionNumber(), targetVersion.filename())
                .orElseThrow(() -> new IllegalStateException("rollback source file missing: " + documentId.value()));
        documentSourceStorage.saveVersion(documentId, newVersionNumber, targetVersion.filename(), sourceContent);

        boolean appended = documentRepository.appendRollbackVersion(
                currentUser.workspaceId(),
                documentId,
                latestVersionNumber,
                rollbackVersion,
                now);
        if (!appended) {
            throw staleLatestVersion();
        }

        Integer askableVersionNumber =
                toNullableVersionNumber(documentRepository.findLatestIndexedVersionNumber(currentUser.workspaceId(), documentId));
        return new DocumentVersionRollbackResult(
                documentId.value(),
                newVersionNumber,
                targetVersion.versionNumber(),
                newVersionNumber,
                askableVersionNumber,
                askableVersionNumber != null,
                UploadStatus.UPLOADED.name(),
                DocumentVersionOriginType.ROLLBACK.name());
    }

    /**
     * 构造回退产生的新版本事实。
     *
     * @param documentId        文档资产 ID
     * @param newVersionNumber  新版本号
     * @param targetVersion     回退来源版本
     * @param now               当前时间
     * @return 新的 ROLLBACK 版本事实
     */
    private static DocumentVersion buildRollbackVersion(
            DocumentId documentId,
            int newVersionNumber,
            DocumentVersion targetVersion,
            Instant now) {
        return new DocumentVersion(
                documentId,
                newVersionNumber,
                DocumentVersionOriginType.ROLLBACK,
                targetVersion.versionNumber(),
                targetVersion.fileHash(),
                targetVersion.filename(),
                targetVersion.fileSize(),
                UploadStatus.UPLOADED,
                null,
                0,
                Document.DEFAULT_RETRY_MAX,
                null,
                null,
                null,
                null,
                0,
                null,
                "version-" + newVersionNumber + "-v1",
                null,
                now,
                now);
    }

    /**
     * 校验当前用户对目标文档的管理权限。
     *
     * @param currentUser 当前用户
     * @param document    目标文档
     */
    private void requireManagePermission(CurrentUser currentUser, Document document) {
        try {
            authorizationService.requireCanManageDocument(
                    currentUser,
                    document.documentId().value(),
                    document.kbId());
        } catch (AccessDeniedException ex) {
            throw business(
                    HttpStatus.FORBIDDEN,
                    "VERSION_ROLLBACK_NO_MANAGE_PERMISSION",
                    "你没有回退该文档版本的权限，请联系管理员");
        }
    }

    /**
     * 将 0 版本号转换为 null。
     *
     * @param versionNumber 版本号
     * @return 正版本号或 null
     */
    private static Integer toNullableVersionNumber(int versionNumber) {
        return versionNumber > 0 ? versionNumber : null;
    }

    /**
     * 构造 latest 乐观并发冲突异常。
     *
     * @return 业务异常
     */
    private static BusinessException staleLatestVersion() {
        return business(
                HttpStatus.CONFLICT,
                "VERSION_CONFLICT_STALE_LATEST_VERSION",
                "当前最新版本已变化，请刷新详情后重试");
    }

    /**
     * 快捷构造业务异常。
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
