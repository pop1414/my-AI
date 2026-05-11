package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import java.util.List;

/**
 * 查询指定成员的文档授权列表用例。
 *
 * <p>以成员为维度查询其在当前工作区内的所有活跃文档授权覆盖记录。
 */
public interface ListMemberDocumentGrantsUseCase {

    /**
     * 执行成员文档授权查询。
     *
     * @param userId 目标成员的用户 ID
     * @return 成员的活跃文档授权列表
     */
    List<DocumentGrantResult> handle(String userId);
}
