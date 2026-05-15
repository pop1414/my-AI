package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.command.RollbackDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionRollbackResult;

/**
 * 文档版本回退用例。
 *
 * <p>回退不是回拨旧版本指针，而是基于目标历史版本创建一个新的最新版本，
 * 并让新版本重新进入 ingest 处理链路。
 */
public interface RollbackDocumentVersionUseCase {

    /**
     * 执行版本回退。
     *
     * @param command 版本回退命令
     * @return 版本回退结果
     */
    DocumentVersionRollbackResult handle(RollbackDocumentVersionCommand command);
}
