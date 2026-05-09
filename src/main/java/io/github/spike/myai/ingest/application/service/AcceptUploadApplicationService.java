package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentIdGenerator;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 受理上传应用服务（Application Service）。
 *
 * <p>该类是六边形架构中的应用层服务，实现
 * {@link io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase}
 * 用例接口，负责"用例编排"。位于 ingest 模块的入站端口适配链中，
 * 上承 REST 控制器，下接领域端口（Document / KnowledgeBase 仓储）。
 *
 * <p>核心职责（按执行顺序）：
 * <ol>
 *   <li>解析目标知识库 ID（为空时回退到默认值）；</li>
 *   <li>调用 {@link AuthorizationService} 校验贡献权限；</li>
 *   <li>校验知识库存在且处于启用状态（Fail-Fast）；</li>
 *   <li>根据文件哈希（SHA-256）实现幂等去重——相同文件复用已有文档 ID；</li>
 *   <li>生成新文档 ID，创建 {@link UploadStatus#UPLOADED} 状态聚合根并持久化；</li>
 *   <li>记录关键链路日志，返回 {@link UploadStatus#ACCEPTED} 票据。</li>
 * </ol>
 *
 * <p>架构约束与设计考量：
 * <ul>
 *   <li>不直接处理 HTTP 请求解析（由 Controller 负责）；</li>
 *   <li>不直接操作文件系统或数据库连接（由 Infrastructure 层实现端口）；</li>
 *   <li>受理阶段为同步操作，仅完成元数据记录——后续的文档解析、
 *       文本提取、智能分块、向量化等由异步 Job 驱动（异步解耦）；</li>
 *   <li>幂等去重基于文件哈希而非文件名——相同内容文件即使名称不同也只存一份；</li>
 *   <li>所有端口依赖通过构造器注入，不依赖 Spring 注解扫描之外的隐式绑定。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class AcceptUploadApplicationService implements AcceptUploadUseCase {

    /** 日志记录器，用于记录上传受理的关键链路日志 */
    private static final Logger log = LoggerFactory.getLogger(AcceptUploadApplicationService.class);

    /**
     * 默认知识库 ID。
     *
     * <p>当前阶段所有上传统一落入默认知识库，前端可不传 kbId。
     * 后续可扩展为多知识库策略，届时此常量可废弃或改为配置项。
     */
    private static final String DEFAULT_KB_ID = "default";

    /**
     * 文档 ID 生成器（领域端口）。
     *
     * <p>用于生成全局唯一的 {@link DocumentId} 值对象，
     * 实际实现由基础设施层提供（如 UUID 或雪花 ID），
     * 通过 Spring 自动注入，应用层不感知具体生成算法。
     */
    private final DocumentIdGenerator documentIdGenerator;

    /**
     * 文档仓储端口：用于持久化文档聚合根及按哈希去重查询。
     *
     * <p>注意：此端口仅负责元数据 CRUD，不涉及文件二进制内容的存储
     * （文件存储由独立的 FileStorage 端口处理）。
     */
    private final DocumentRepository documentRepository;

    /**
     * 知识库仓储端口：用于校验目标知识库的存在性与状态。
     *
     * <p>仅调用 {@code findByKbId} 查询方法，不执行写操作，
     * 确保上传受理不越权修改知识库数据。
     */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 当前用户上下文提供器，用于获取当前登录用户的工作区标识。
     *
     * <p>上传受理需要明确文档归属的工作空间，
     * 通过此提供器从安全上下文中提取工作区 ID。
     */
    private final CurrentUserProvider currentUserProvider;

    /**
     * 应用层授权服务，用于校验当前用户是否具备知识库贡献权限。
     *
     * <p>上传文档属于知识库贡献操作，需要
     * {@link io.github.spike.myai.auth.domain.model.KnowledgeBaseRole#KB_MANAGER}
     * 或 {@link io.github.spike.myai.auth.domain.model.KnowledgeBaseRole#KB_CONTRIBUTOR}
     * 角色（或工作区 OWNER / ADMIN）。
     */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * <p>所有依赖均为领域端口或应用层服务，不依赖基础设施层具体实现。
     * Spring 自动装配时，实际注入的是各端口的基础设施层适配器
     * （如 JdbcXxxRepository）。
     *
     * @param documentIdGenerator     文档 ID 生成器（领域端口）
     * @param documentRepository      文档仓储（领域端口）
     * @param knowledgeBaseRepository 知识库仓储（领域端口）
     * @param currentUserProvider     当前用户上下文提供器（应用层端口）
     * @param authorizationService    授权服务（应用层）
     */
    public AcceptUploadApplicationService(
            DocumentIdGenerator documentIdGenerator,
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentIdGenerator = documentIdGenerator;
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 执行上传受理流程。
     *
     * <p>处理步骤（严格按顺序，每步失败即终止）：
     * <ol>
     *   <li><strong>解析知识库 ID：</strong>为空时使用默认值；</li>
     *   <li><strong>权限校验：</strong>调用 {@code requireCanContributeKnowledgeBase}，
     *       确保当前用户（或工作区管理员）可向目标知识库贡献内容；</li>
     *   <li><strong>获取工作区：</strong>从当前安全上下文提取工作空间标识，
     *       后续查询与写入均限定在此工作区内；</li>
     *   <li><strong>知识库校验：</strong>检查存在性 + 启用状态，不满足则快速失败；</li>
     *   <li><strong>幂等去重：</strong>通过文件哈希（SHA-256）检查是否已有相同文件，
     *       已存在则直接复用已有文档 ID 并返回 ACCEPTED；</li>
     *   <li><strong>创建文档记录：</strong>生成新文档 ID，创建 UPLOADED 状态
     *       聚合根并持久化，后续异步 Job 轮询此状态推进处理链路；</li>
     *   <li><strong>日志与返回：</strong>记录关键链路日志，返回 ACCEPTED 票据。</li>
     * </ol>
     *
     * @param command 用例输入参数（含文件名、大小、SHA-256 哈希、可选 kbId）
     * @return 受理票据，包含文档 ID 与 ACCEPTED 状态
     * @throws KnowledgeBaseNotFoundException 当目标知识库不存在时
     * @throws KnowledgeBaseInactiveException 当目标知识库已停用时
     * @throws org.springframework.security.access.AccessDeniedException 权限不足时（由授权服务抛出）
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException 未认证时（由用户提供器抛出）
     */
    @Override
    public UploadTicket handle(AcceptUploadCommand command) {
        // ---------- 步骤1：解析知识库 ID ----------
        // 前端可不传 kbId（为空时回退到默认知识库 "default"）
        String resolvedKbId = resolveKbId(command.kbId());

        // ---------- 步骤2：权限校验 ----------
        // 上传文档属于知识库贡献操作，需 KB_MANAGER / KB_CONTRIBUTOR 或工作区管理员
        // 权限不足时直接抛出 AccessDeniedException，不创建任何记录
        authorizationService.requireCanContributeKnowledgeBase(resolvedKbId);

        // ---------- 步骤3：获取工作区上下文 ----------
        // 从安全上下文中提取当前用户的工作空间 ID，后续所有查询和写入限定在此工作区内
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String workspaceId = currentUser.workspaceId();

        // ---------- 步骤4：校验目标知识库 ----------
        // 检查存在性 + 启用状态，不满足则快速失败（Fail-Fast）
        validateKnowledgeBase(workspaceId, resolvedKbId);

        String fileHash = command.fileHash();

        // ---------- 步骤5：幂等去重 ----------
        // 通过文件哈希（SHA-256）检查是否已有内容相同的文件
        // 若已存在则直接复用已有 DocumentId，避免分配新 ID 导致存储冗余
        Document existingDocument =
                documentRepository.findByKbIdAndFileHash(workspaceId, resolvedKbId, fileHash).orElse(null);

        if (existingDocument != null) {
            // 幂等命中：相同文件已受理过，直接返回已有票据
            // 记录日志以便追踪重复上传的来源和频率
            log.info(
                    "Duplicate upload accepted with existing document. documentId={}, kbId={}, filename={}, fileHash={}",
                    existingDocument.documentId().value(),
                    resolvedKbId,
                    command.filename(),
                    fileHash);
            return new UploadTicket(existingDocument.documentId(), UploadStatus.ACCEPTED);
        }

        // ---------- 步骤6：创建新文档记录 ----------
        // 生成全局唯一文档 ID
        DocumentId documentId = documentIdGenerator.nextId();
        // 记录当前时间戳，作为文档的创建时间和更新时间
        Instant now = Instant.now();

        // 创建 UPLOADED 状态的文档聚合根
        // 后续异步 Job（如 IngestProcessingJob）会轮询 UPLOADED 状态的文档
        // 并推进处理链路：文本提取 → 智能分块 → 向量化 → 标记为 INDEXED
        Document document =
                Document.uploaded(
                        documentId,
                        workspaceId,
                        resolvedKbId,
                        fileHash,
                        command.filename(),
                        command.fileSize(),
                        now);
        // 持久化文档聚合根到存储层
        documentRepository.save(document);

        // ---------- 步骤7：记录日志并返回票据 ----------
        // 记录关键链路日志：文档 ID、知识库、文件名、大小、哈希
        // 便于后续排查"文档是否已受理"的问题
        log.info(
                "Accepted upload request. documentId={}, kbId={}, filename={}, fileSize={}, fileHash={}",
                documentId.value(),
                resolvedKbId,
                command.filename(),
                command.fileSize(),
                fileHash);

        // 返回 ACCEPTED 票据——表示"请求已受理，异步处理中"
        // 客户端可使用 documentId 轮询处理进度
        return new UploadTicket(documentId, UploadStatus.ACCEPTED);
    }

    /**
     * 解析知识库 ID。
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>若传入 {@code kbId} 为 {@code null} 或空白字符串 →
     *       返回默认知识库 ID（{@value #DEFAULT_KB_ID}）；</li>
     *   <li>否则 → 去除首尾空白后返回。</li>
     * </ul>
     *
     * <p>此方法不校验知识库是否存在——存在性校验由
     * {@link #validateKnowledgeBase} 在后续步骤中完成。
     *
     * @param kbId 请求传入的知识库 ID（可能为 {@code null} 或空）
     * @return 非空、非空白且已 trim 的知识库 ID
     */
    private String resolveKbId(String kbId) {
        // 前端未传 kbId 或传入空字符串时，回退到默认知识库
        if (kbId == null || kbId.isBlank()) {
            return DEFAULT_KB_ID;
        }
        // 去除首尾空白，防止意外空格导致查询失败
        return kbId.trim();
    }

    /**
     * 校验知识库存在且处于启用状态。
     *
     * <p>校验逻辑分两步（Fail-Fast 模式）：
     * <ol>
     *   <li>按工作空间 + 知识库 ID 查找知识库，不存在则抛出
     *       {@link KnowledgeBaseNotFoundException}；</li>
     *   <li>检查知识库状态是否为 {@link KnowledgeBaseStatus#ACTIVE}，
     *       非启用状态（如 ARCHIVED / DISABLED）则抛出
     *       {@link KnowledgeBaseInactiveException}。</li>
     * </ol>
     *
     * <p>设计考量：
     * <ul>
     *   <li>每次上传受理均执行此校验，确保停用的知识库无法接收新文档；</li>
     *   <li>使用 {@code orElseThrow} 而非返回 {@code null}，\n     *       避免空指针传播到后续流程；</li>
     *   <li>工作空间+知识库 ID 联合查询，防止跨工作区越权访问。</li>
     * </ul>
     *
     * @param workspaceId 工作空间标识
     * @param kbId        待校验的知识库 ID
     * @throws KnowledgeBaseNotFoundException 当指定工作空间下不存在该知识库时
     * @throws KnowledgeBaseInactiveException 当知识库处于非启用状态时
     */
    private void validateKnowledgeBase(String workspaceId, String kbId) {
        // 1. 查找知识库，不存在则快速失败
        var knowledgeBase = knowledgeBaseRepository.findByKbId(workspaceId, kbId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException("knowledge base not found: " + kbId));

        // 2. 状态校验：仅 ACTIVE 状态允许接收新文档
        if (knowledgeBase.status() != KnowledgeBaseStatus.ACTIVE) {
            throw new KnowledgeBaseInactiveException("knowledge base is inactive: " + kbId);
        }
    }
}
