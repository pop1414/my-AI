package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;

/**
 * 文档资产删除用例接口。
 */
public interface DeleteDocumentUseCase {

    /**
     * 删除文档资产（源文件 + 向量），并将状态置为 DELETED。
     *
     * @param command 用例输入
     */
    void handle(DeleteDocumentCommand command);
}
