package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import org.springframework.stereotype.Service;

/**
 * 查询文档状态应用服务（Application Service）。
 *
 * <p>该服务实现 {@link GetDocumentStatusUseCase} 用例接口，职责：
 * <ol>
 *   <li>接收查询参数并构造领域值对象；</li>
 *   <li>通过仓储端口查询文档元数据；</li>
 *   <li>将领域对象映射为应用层返回模型。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class GetDocumentStatusApplicationService implements GetDocumentStatusUseCase {

    /** 文档仓储端口 */
    private final DocumentRepository documentRepository;

    /**
     * 构造器注入。
     *
     * @param documentRepository 文档仓储
     */
    public GetDocumentStatusApplicationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 查询文档当前处理状态。
     *
     * <p>用于前端轮询文档入库进度（如是否已完成向量化等）。
     *
     * @param query 查询参数（含文档 ID）
     * @return 文档状态结果（含 documentId 与 status）
     * @throws DocumentNotFoundException 当文档不存在时
     */
    @Override
    public DocumentStatusResult handle(GetDocumentStatusQuery query) {
        DocumentId documentId = new DocumentId(query.documentId());
        Document document = documentRepository.findById(WorkspaceConstants.DEFAULT_WORKSPACE_ID, documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        return new DocumentStatusResult(document.documentId(), document.status());
    }
}
