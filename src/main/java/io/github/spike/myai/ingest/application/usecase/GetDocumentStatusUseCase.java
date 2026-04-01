package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;

/**
 * 查询文档状态用例接口（Application Layer UseCase）。
 */
public interface GetDocumentStatusUseCase {

    /**
     * 查询指定文档的当前状态。
     *
     * @param query 查询参数
     * @return 查询结果
     */
    DocumentStatusResult handle(GetDocumentStatusQuery query);
}
