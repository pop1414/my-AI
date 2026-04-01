package io.github.spike.myai.ingest.application.result;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;

/**
 * 查询文档状态用例的返回结果（Application Result）。
 *
 * @param documentId 文档 ID
 * @param status 当前处理状态
 */
public record DocumentStatusResult(DocumentId documentId, UploadStatus status) {
}
