package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentIdGenerator;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 受理上传应用服务（Application Service）。
 *
 * <p>该类负责“用例编排”，核心职责：
 * <ul>
 *     <li>处理用例级输入（如 kbId 默认值解析）。</li>
 *     <li>调用领域端口（DocumentIdGenerator）获取领域对象。</li>
 *     <li>创建文档聚合并落库，保证上传受理可追踪。</li>
 *     <li>生成并返回接口语义结果（UploadTicket）。</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *     <li>这里不直接包含 HTTP 逻辑（由 Controller 处理）。</li>
 *     <li>这里不直接包含基础设施实现细节（由 Infrastructure 层实现端口）。</li>
 * </ul>
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

    public AcceptUploadApplicationService(
            DocumentIdGenerator documentIdGenerator,
            DocumentRepository documentRepository) {
        this.documentIdGenerator = documentIdGenerator;
        this.documentRepository = documentRepository;
    }

    /**
     * 执行上传受理流程。
     *
     * @param command 用例输入参数
     * @return 受理票据（包含文档 ID 与状态）
     */
    @Override
    public UploadTicket handle(AcceptUploadCommand command) {
        String resolvedKbId = resolveKbId(command.kbId());
        String fileHash = command.fileHash();
        // 通过Hash检查正在处理的文件，是否已经存在在数据库中
        Document existingDocument =
                documentRepository.findByKbIdAndFileHash(resolvedKbId, fileHash).orElse(null);
        // 已经存在，就不新分配DocumentId了，以免发生冲突
        // 直接返回存在的文件信息
        if (existingDocument != null) {
            log.info(
                    "Duplicate upload accepted with existing document. documentId={}, kbId={}, filename={}, fileHash={}",
                    existingDocument.documentId().value(),
                    resolvedKbId,
                    command.filename(),
                    fileHash);
            return new UploadTicket(existingDocument.documentId(), UploadStatus.ACCEPTED);
        }

        DocumentId documentId = documentIdGenerator.nextId();
        Instant now = Instant.now();

        // 先持久化一条 UPLOADED 状态文档，后续异步链路可基于该记录推进状态机。
        Document document =
                Document.uploaded(documentId, resolvedKbId, fileHash, command.filename(), command.fileSize(), now);
        documentRepository.save(document);

        // 记录关键链路日志，便于后续定位上传请求是否进入应用层。
        log.info(
                "Accepted upload request. documentId={}, kbId={}, filename={}, fileSize={}, fileHash={}",
                documentId.value(),
                resolvedKbId,
                command.filename(),
                command.fileSize(),
                fileHash);
        // 对外接口返回语义保持 ACCEPTED，表示“请求已受理”。
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
        return kbId;
    }
}
