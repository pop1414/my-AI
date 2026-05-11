package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.ReplaceDocumentMemberGrantsCommand;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import java.util.List;

/**
 * 以文档为维度批量覆盖成员授权用例。
 *
 * <p>将指定文档的成员授权集合整体替换为期望的授权列表，
 * 不在期望列表中的现有授权将被软删除，
 * 授权变更在同一数据库事务中原子完成。
 */
public interface ReplaceDocumentMemberGrantsUseCase {

    /**
     * 执行文档成员授权批量替换。
     *
     * @param command 替换命令，包含目标文档 ID 和期望授权列表
     * @return 替换后的完整活跃授权列表
     */
    List<DocumentGrantResult> handle(ReplaceDocumentMemberGrantsCommand command);
}
