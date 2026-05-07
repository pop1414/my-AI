package io.github.spike.myai.ingest.application.service;

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
 * <p>该类是六边形架构中的应用层服务，负责"用例编排"，核心职责：
 * <ol>
 *   <li>处理用例级输入（如 {@code kbId} 默认值解析）；</li>
 *   <li>调用领域端口（{@link DocumentIdGenerator}）获取领域对象；</li>
 *   <li>创建文档聚合并落库，保证上传受理可追踪；</li>
 *   <li>根据文件哈希实现幂等去重，避免重复分配文档 ID；</li>
 *   <li>校验目标知识库是否存在且处于启用状态；</li>
 *   <li>生成并返回接口语义结果（{@link UploadTicket}）。</li>
 * </ol>
 *
 * <p>注意：
 * <ul>
 *   <li>这里不直接包含 HTTP 逻辑（由 Controller 处理）；</li>
 *   <li>这里不直接包含基础设施实现细节（由 Infrastructure 层实现端口）；</li>
 *   <li>受理是同步操作（创建记录 + 存储源文件），
 *       后续的解析/分块/向量化由异步 Job 驱动。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class AcceptUploadApplicationService implements AcceptUploadUseCase {

    private static final Logger log = LoggerFactory.getLogger(AcceptUploadApplicationService.class);
    /**
     * 默认知识库 ID。当前阶段统一落到 default，后续可扩展为多知识库策略。
     */
    private static final String DEFAULT_KB_ID = "default";

    /**
     * 领域端口：文档 ID 生成器。
     * 实际实现由基础设施层提供并由 Spring 自动注入。
     */
    private final DocumentIdGenerator documentIdGenerator;
    /**
     * 文档仓储端口：用于持久化文档元数据状态。
     */
    private final DocumentRepository documentRepository;

    /** 知识库仓储端口：用于校验目标知识库的存在性与状态 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 构造器注入。
     *
     * @param documentIdGenerator      文档 ID 生成器
     * @param documentRepository       文档仓储
     * @param knowledgeBaseRepository  知识库仓储
     */
    public AcceptUploadApplicationService(
            DocumentIdGenerator documentIdGenerator,
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository) {
        this.documentIdGenerator = documentIdGenerator;
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 执行上传受理流程。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>解析目标知识库 ID（为空时使用默认值）；</li>
     *   <li>校验知识库存在且处于启用状态；</li>
     *   <li>根据文件哈希检查是否已有相同文件（幂等去重）；</li>
     *   <li>若已存在，直接复用已有文档 ID 并返回；</li>
     *   <li>若不存在，生成新文档 ID，创建 UPLOADED 状态记录并持久化；</li>
     *   <li>记录关键链路日志，返回 ACCEPTED 票据。</li>
     * </ol>
     *
     * @param command 用例输入参数（含文件名、大小、哈希、kbId）
     * @return 受理票据（包含文档 ID 与状态）
     * @throws KnowledgeBaseNotFoundException 当目标知识库不存在时
     * @throws KnowledgeBaseInactiveException 当目标知识库已停用时
     */
    @Override
    public UploadTicket handle(AcceptUploadCommand command) {
        // 1. 解析知识库 ID：为空或空白时回退到默认知识库
        // 1. 解析知识库 ID：为空或空白时回退到默认知识库
        String resolvedKbId = resolveKbId(command.kbId());

        // 2. 校验目标知识库：存在性 + 启用状态，不满足则快速失败
        validateKnowledgeBase(resolvedKbId);

        String fileHash = command.fileHash();

        // 3. 幂等去重：通过文件哈希检查是否已有相同文件在库中
        //    若存在则直接复用已有的 DocumentId，避免分配新 ID 导致数据冗余
        Document existingDocument =
                documentRepository.findByKbIdAndFileHash(resolvedKbId, fileHash).orElse(null);

        if (existingDocument != null) {
            // 命中幂等：相同文件已受理过，直接返回已有票据
            log.info(
                    "Duplicate upload accepted with existing document. documentId={}, kbId={}, filename={}, fileHash={}",
                    existingDocument.documentId().value(),
                    resolvedKbId,
                    command.filename(),
                    fileHash);
            return new UploadTicket(existingDocument.documentId(), UploadStatus.ACCEPTED);
        }

        // 4. 生成新的文档 ID 并记录当前时间戳
        DocumentId documentId = documentIdGenerator.nextId();
        Instant now = Instant.now();

        // 5. 创建 UPLOADED 状态的文档聚合根并持久化
        //    后续异步 Job 会轮询该状态并推进处理链路（解析 → 分块 → 向量化）
        Document document =
                Document.uploaded(documentId, resolvedKbId, fileHash, command.filename(), command.fileSize(), now);
        documentRepository.save(document);

        // 6. 记录关键链路日志，便于后续定位上传请求是否进入应用层
        log.info(
                "Accepted upload request. documentId={}, kbId={}, filename={}, fileSize={}, fileHash={}",
                documentId.value(),
                resolvedKbId,
                command.filename(),
                command.fileSize(),
                fileHash);

        // 7. 返回 ACCEPTED 票据，表示"请求已受理，异步处理中"
        return new UploadTicket(documentId, UploadStatus.ACCEPTED);
    }

    /**
     * 解析知识库 ID。
     *
     * @param kbId 请求传入的知识库 ID
     * @return 非空 kbId；若为空则返回默认值
     */
    private String resolveKbId(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return DEFAULT_KB_ID;
        }
        return kbId.trim();
    }

    /**
     * 校验知识库存在且处于启用状态。
     *
     * <p>校验逻辑分两步：
     * <ol>
     *   <li>按 {@code kbId} 查找知识库，不存在则抛出
     *       {@link KnowledgeBaseNotFoundException}；</li>
     *   <li>检查知识库状态是否为 {@link KnowledgeBaseStatus#ACTIVE}，
     *       非启用状态则抛出 {@link KnowledgeBaseInactiveException}。</li>
     * </ol>
     *
     * <p>注意：该方法在每次上传受理时均会执行，
     * 确保停用的知识库无法接收新文档。
     *
     * @param kbId 待校验的知识库 ID
     * @throws KnowledgeBaseNotFoundException 当知识库不存在时
     * @throws KnowledgeBaseInactiveException 当知识库处于非启用状态时
     */
    private void validateKnowledgeBase(String kbId) {
        // 1. 查找知识库，不存在则快速失败
        var knowledgeBase = knowledgeBaseRepository.findByKbId(kbId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException("knowledge base not found: " + kbId));

        // 2. 状态校验：仅 ACTIVE 状态允许接收新文档
        if (knowledgeBase.status() != KnowledgeBaseStatus.ACTIVE) {
            throw new KnowledgeBaseInactiveException("knowledge base is inactive: " + kbId);
        }
    }
}
