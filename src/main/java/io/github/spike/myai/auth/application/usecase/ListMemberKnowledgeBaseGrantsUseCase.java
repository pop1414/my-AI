package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import java.util.List;

/**
 * 查询指定成员的知识库授权列表用例。
 *
 * <p>以成员为维度查询其在当前工作区内的所有活跃知识库授权记录。
 */
public interface ListMemberKnowledgeBaseGrantsUseCase {

    /**
     * 执行成员知识库授权查询。
     *
     * @param userId 目标成员的用户 ID
     * @return 成员的活跃知识库授权列表
     */
    List<KnowledgeBaseGrantResult> handle(String userId);
}
