package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.GetDocumentContentQuery;
import io.github.spike.myai.ingest.application.result.DocumentContentResult;

/**
 * 文档 latest 正文读取用例。
 *
 * <p>实现类必须读取当前 latest version 的版本级 {@code cleaned.md}，
 * 不得自动回退到旧版本正文。
 */
public interface GetDocumentContentUseCase {

    /**
     * 读取文档 latest 正文。
     *
     * @param query 正文读取查询
     * @return 文档正文与版本上下文
     */
    DocumentContentResult handle(GetDocumentContentQuery query);
}
