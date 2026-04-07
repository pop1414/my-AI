package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;

/**
 * 查询文档分块预览用例。
 */
public interface GetDocumentChunksPreviewUseCase {

    /**
     * 查询文档分块预览。
     *
     * @param query 查询参数
     * @return 预览结果
     */
    DocumentChunksPreviewResult handle(GetDocumentChunksPreviewQuery query);
}

