package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.ingest.application.command.UploadNewDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionUploadResult;
import io.github.spike.myai.ingest.application.usecase.UploadNewDocumentVersionUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersion;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.shared.rest.BusinessException;
import java.time.Instant;

import io.github.spike.myai.shared.rest.GlobalRestExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 上传既有 document 新版本应用服务。
 *
 * <p>实现 {@link UploadNewDocumentVersionUseCase} 用例，负责编排上传新版本的完整流程：
 * <ol>
 *   <li>获取当前用户并查询目标文档；</li>
 *   <li>校验管理权限（通过 {@link AuthorizationService#requireCanManageDocument}）；</li>
 *   <li>校验 expectedLatestVersionNumber 防并发冲突；</li>
 *   <li>校验文档状态（仅 INDEXED / FAILED 允许上传新版本）；</li>
 *   <li>同内容幂等复用：fileHash 一致时返回 REUSED_IDENTICAL_CONTENT；</li>
 *   <li>创建新版本事实行并追加到仓储；</li>
 *   <li>持久化源文件到版本化存储。</li>
 * </ol>
 *
 * <p>所有业务校验失败均通过 {@link BusinessException} 抛出，
 * 由 {@link GlobalRestExceptionHandler} 统一转换为结构化 JSON 错误响应。
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
@Transactional
public class UploadNewDocumentVersionApplicationService implements UploadNewDocumentVersionUseCase {

    /** 结果类型：成功创建新版本 */
    static final String RESULT_CREATED = "CREATED";
    /** 结果类型：因内容相同触发幂等复用，未创建新版本 */
    static final String RESULT_REUSED_IDENTICAL_CONTENT = "REUSED_IDENTICAL_CONTENT";

    /** 文档元数据仓储 */
    private final DocumentRepository documentRepository;
    /** 源文件存储（支持版本化存取） */
    private final DocumentSourceStorage documentSourceStorage;
    /** 当前用户提供者 */
    private final CurrentUserProvider currentUserProvider;
    /** 权限校验服务 */
    private final AuthorizationService authorizationService;
    /** 审计事件仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入。
     *
     * @param documentRepository     文档元数据仓储
     * @param documentSourceStorage  源文件存储
     * @param currentUserProvider    当前用户提供者
     * @param authorizationService   权限校验服务
     * @param auditEventRepository   审计事件仓储
     */
    @Autowired
    public UploadNewDocumentVersionApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService,
            AuditEventRepository auditEventRepository) {
        this.documentRepository = documentRepository;
        this.documentSourceStorage = documentSourceStorage;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 兼容构造器：未显式接入审计仓储的单元测试保持原有调用形态。
     *
     * @param documentRepository     文档元数据仓储
     * @param documentSourceStorage  源文件存储
     * @param currentUserProvider    当前用户提供者
     * @param authorizationService   权限校验服务
     */
    UploadNewDocumentVersionApplicationService(
            DocumentRepository documentRepository,
            DocumentSourceStorage documentSourceStorage,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this(
                documentRepository,
                documentSourceStorage,
                currentUserProvider,
                authorizationService,
                event -> {
                });
    }

    /**
     * 处理上传新版本命令的核心编排逻辑。
     *
     * <p>该方法按照以下步骤顺序执行，任一步骤失败即终止并抛出对应异常：
     * <ol>
     *   <li>获取当前用户身份；</li>
     *   <li>查询目标文档（不存在则抛出 404）；</li>
     *   <li>校验管理权限；</li>
     *   <li>校验 expectedLatestVersionNumber（乐观锁）；</li>
     *   <li>校验文档状态（仅 INDEXED / FAILED 允许）；</li>
     *   <li>同内容幂等复用检查；</li>
     *   <li>创建新版本事实并持久化。</li>
     * </ol>
     *
     * @param command 上传新版本命令
     * @return 版本上传结果
     * @throws BusinessException 当校验失败时
     */
    @Override
    public DocumentVersionUploadResult handle(UploadNewDocumentVersionCommand command) {
        // 步骤 1：获取当前用户身份，用于后续权限校验和 workspace 上下文
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        DocumentId documentId = new DocumentId(command.normalizedDocumentId());

        // 步骤 2：查询目标文档，不存在则抛 404
        Document document = documentRepository.findById(currentUser.workspaceId(), documentId)
                .orElseThrow(() -> business(
                        HttpStatus.NOT_FOUND,
                        "VERSION_UPLOAD_DOCUMENT_NOT_FOUND",
                        "document not found: " + documentId.value()));

        // 步骤 3：校验当前用户对该文档的管理权限
        requireManagePermission(currentUser, document);

        // 步骤 4：乐观锁校验 —— expectedLatestVersionNumber 必须与当前 latest 一致
        int latestVersionNumber = document.latestVersionNumber();
        if (command.expectedLatestVersionNumber() != latestVersionNumber) {
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STALE_LATEST_VERSION",
                    "当前最新版本已变化，请刷新详情后重试");
        }

        // 步骤 5：状态校验 —— 仅 INDEXED 或 FAILED 状态下允许上传新版本
        if (document.status() != UploadStatus.INDEXED && document.status() != UploadStatus.FAILED) {
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_UPLOAD_NOT_ALLOWED_STATUS",
                    "上传新版本仅允许在当前最新版本为 INDEXED 或 FAILED 时发起");
        }

        // 步骤 6：查询当前可问答版本号（最大的 INDEXED 版本号，0 表示无）
        Integer askableVersionNumber =
                toNullableVersionNumber(documentRepository.findLatestIndexedVersionNumber(currentUser.workspaceId(), documentId));

        // 步骤 7：同内容幂等复用 —— 如果 fileHash 与当前 latest 一致，不创建新版本
        if (document.fileHash().equals(command.fileHash())) {
            auditEventRepository.save(IngestAuditEvents.documentVersionUploadRequested(
                    currentUser,
                    documentId,
                    document.kbId(),
                    latestVersionNumber,
                    latestVersionNumber,
                    command.filename(),
                    command.fileSize(),
                    command.fileHash(),
                    RESULT_REUSED_IDENTICAL_CONTENT,
                    Instant.now()));
            return new DocumentVersionUploadResult(
                    documentId.value(),
                    false,                              // 未创建新版本
                    RESULT_REUSED_IDENTICAL_CONTENT,    // 结果类型：幂等复用
                    null,                               // 无新版本号
                    latestVersionNumber,
                    latestVersionNumber,
                    latestVersionNumber,
                    askableVersionNumber,
                    askableVersionNumber != null,        // 是否存在可问答版本
                    document.status().name(),
                    document.latestVersionOriginType().name());
        }

        // 步骤 8：创建新版本事实 —— 版本号递增，状态为 UPLOADED
        int newVersionNumber = latestVersionNumber + 1;
        Instant now = Instant.now();
        DocumentVersion newVersion = new DocumentVersion(
                documentId,
                newVersionNumber,
                DocumentVersionOriginType.UPLOAD,        // 来源类型：用户上传
                null,                                    // 非回滚版本，无需记录回滚来源
                command.fileHash(),
                command.filename(),
                command.fileSize(),
                UploadStatus.UPLOADED,                   // 初始状态：待处理
                null,                                    // 无失败原因
                0,                                       // 重试次数从 0 开始
                Document.DEFAULT_RETRY_MAX,               // 使用默认最大重试次数
                null,                                    // nextRetryAt 为空
                null, null, null,                        // 无错误信息
                0,                                       // reprocessCount 为 0
                null,                                    // reprocessRequestedAt 为空
                "version-" + newVersionNumber + "-v1",   // 分块版本号
                null,                                    // processingMetadata 为空
                currentUser.userId(),                     // 创建人：当前上传操作者
                now,
                now);

        // 步骤 9：通过 CAS 追加新版本到主表 latest 快照
        boolean appended = documentRepository.appendUploadVersion(
                currentUser.workspaceId(),
                documentId,
                latestVersionNumber,    // CAS 条件：期望当前 latestVersionNumber
                newVersion,
                now);
        if (!appended) {
            // CAS 失败说明在步骤 4~9 之间版本号已被其他请求修改
            throw business(
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT_STALE_LATEST_VERSION",
                    "当前最新版本已变化，请刷新详情后重试");
        }
        // 步骤 10：DB 版本事实已写入当前事务后，再保存版本源文件；保存失败会触发事务回滚。
        saveNewVersionSource(documentId, newVersionNumber, command.filename(), command.sourceContent());

        // 步骤 11：追加成功后重新查询可问答版本号（可能与追加前一致）
        Integer updatedAskableVersionNumber =
                toNullableVersionNumber(documentRepository.findLatestIndexedVersionNumber(currentUser.workspaceId(), documentId));
        auditEventRepository.save(IngestAuditEvents.documentVersionUploadRequested(
                currentUser,
                documentId,
                document.kbId(),
                newVersionNumber,
                latestVersionNumber,
                command.filename(),
                command.fileSize(),
                command.fileHash(),
                RESULT_CREATED,
                now));
        return new DocumentVersionUploadResult(
                documentId.value(),
                true,                                   // 成功创建新版本
                RESULT_CREATED,
                newVersionNumber,
                latestVersionNumber,
                null,                                   // 非复用场景，无 reusedLatestVersionNumber
                newVersionNumber,                       // 当前最新版本号
                updatedAskableVersionNumber,
                updatedAskableVersionNumber != null,
                UploadStatus.UPLOADED.name(),
                DocumentVersionOriginType.UPLOAD.name());
    }

    /**
     * 保存上传新版本源文件。
     *
     * @param documentId    文档资产 ID
     * @param versionNumber 新版本号
     * @param filename      文件名
     * @param sourceContent 文件内容
     */
    private void saveNewVersionSource(
            DocumentId documentId,
            int versionNumber,
            String filename,
            byte[] sourceContent) {
        try {
            documentSourceStorage.saveVersionIfAbsent(documentId, versionNumber, filename, sourceContent);
        } catch (IllegalStateException ex) {
            if (DocumentSourceStorage.VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE.equals(ex.getMessage())) {
                throw business(
                        HttpStatus.CONFLICT,
                        "VERSION_CONFLICT_STALE_LATEST_VERSION",
                        "当前最新版本已变化，请刷新详情后重试");
            }
            throw ex;
        }
    }

    /**
     * 校验当前用户对目标文档的管理权限。
     *
     * <p>通过 {@link AuthorizationService#requireCanManageDocument} 进行权限检查，
     * 若权限不足则捕获 {@link AccessDeniedException} 并转换为携带业务错误码的
     * {@link BusinessException}，便于前端展示精确的权限提示。
     *
     * @param currentUser 当前用户
     * @param document    目标文档
     * @throws BusinessException 当用户无管理权限时，错误码为 VERSION_UPLOAD_NO_MANAGE_PERMISSION
     */
    private void requireManagePermission(CurrentUser currentUser, Document document) {
        try {
            authorizationService.requireCanManageDocument(
                    currentUser,
                    document.documentId().value(),
                    document.kbId());
        } catch (AccessDeniedException ex) {
            // 将通用权限异常转换为携带业务错误码的 BusinessException
            throw business(
                    HttpStatus.FORBIDDEN,
                    "VERSION_UPLOAD_NO_MANAGE_PERMISSION",
                    "你没有管理该文档版本的权限，请联系管理员");
        }
    }

    /**
     * 将 int 版本号转换为可空的 Integer。
     *
     * <p>版本号 0 表示「不存在可问答版本」，对外应表示为 null。
     *
     * @param versionNumber 版本号（0 表示无）
     * @return 正版本号原样返回，0 时返回 null
     */
    private static Integer toNullableVersionNumber(int versionNumber) {
        return versionNumber > 0 ? versionNumber : null;
    }

    /**
     * 快捷构造 {@link BusinessException} 的工厂方法。
     *
     * @param status  HTTP 状态码
     * @param code    业务错误码
     * @param message 错误消息
     * @return 构造好的 BusinessException 实例
     */
    private static BusinessException business(HttpStatus status, String code, String message) {
        return new BusinessException(status, code, message);
    }
}
