package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import org.springframework.stereotype.Service;

/**
 * 查询文档状态应用服务（Application Service）。
 *
 * <p>职责：
 * <ul>
 *     <li>接收查询参数并构造领域值对象。</li>
 *     <li>通过仓储端口查询文档元数据。</li>
 *     <li>将领域对象映射为应用层返回模型。</li>
 * </ul>
 */
@Service
public class GetDocumentStatusApplicationService implements GetDocumentStatusUseCase {

    private final DocumentRepository documentRepository;

    public GetDocumentStatusApplicationService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * 查询文档当前状态。
     *
     * @param query 查询参数
     * @return 文档状态结果
     * @throws DocumentNotFoundException 当文档不存在时抛出
     */
    @Override
    public DocumentStatusResult handle(GetDocumentStatusQuery query) {
        DocumentId documentId = new DocumentId(query.documentId());
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId.value()));
        return new DocumentStatusResult(document.documentId(), document.status());
    }
}
