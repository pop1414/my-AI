package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.ReplaceMemberDocumentGrantsCommand;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import java.util.List;

/**
 * 以成员为维度批量覆盖文档授权用例。
 *
 * <p>将指定成员的文档授权集合整体替换为期望的授权列表，
 * 不在期望列表中的现有授权将被软删除，
 * 授权变更在同一数据库事务中原子完成。
 */
public interface ReplaceMemberDocumentGrantsUseCase {

    /**
     * 执行成员文档授权批量替换。
     *
     * @param command 替换命令，包含目标成员 ID 和期望授权列表
     * @return 替换后的完整活跃授权列表
     */
    List<DocumentGrantResult> handle(ReplaceMemberDocumentGrantsCommand command);
}
