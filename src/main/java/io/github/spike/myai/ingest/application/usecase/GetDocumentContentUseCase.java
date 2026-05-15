package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.GetDocumentContentQuery;
import io.github.spike.myai.ingest.application.result.DocumentContentResult;

/**
 * 文档正文读取用例。
 *
 * <p>实现类必须按查询来源选择版本：{@code LATEST} 读取当前最新版本，
 * {@code ASKABLE_BASELINE} 读取当前 QA 可问答基线版本。
 */
public interface GetDocumentContentUseCase {

    /**
     * 读取文档正文。
     *
     * @param query 正文读取查询
     * @return 文档正文与版本上下文
     */
    DocumentContentResult handle(GetDocumentContentQuery query);
}
