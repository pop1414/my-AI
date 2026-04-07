package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.domain.model.DocumentId;

/**
 * 文档处理执行用例。
 */
public interface ProcessDocumentUseCase {

    /**
     * 执行指定文档资产的处理链路。
     *
     * @param documentId 文档资产 ID
     */
    void handle(DocumentId documentId);
}

