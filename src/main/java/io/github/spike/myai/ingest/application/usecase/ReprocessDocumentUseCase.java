package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;

/**
 * 文档重处理用例接口。
 */
public interface ReprocessDocumentUseCase {

    /**
     * 触发文档重处理。
     *
     * @param command 用例输入
     * @return 文档当前状态
     */
    DocumentStatusResult handle(ReprocessDocumentCommand command);
}
