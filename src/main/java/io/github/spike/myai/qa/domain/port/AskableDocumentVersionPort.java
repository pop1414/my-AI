package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.auth.application.context.CurrentUser;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 问答可用文档版本查询端口。
 *
 * <p>应用层通过该端口按文档集合批量查询“当前可问答版本”，
 * 避免把版本链 SQL 或持久化结构泄露到问答编排逻辑中。</p>
 */
public interface AskableDocumentVersionPort {

    /**
     * 批量查询文档当前可问答版本。
     *
     * <p>仅返回存在最近 INDEXED 版本的文档；没有任何可问答版本的文档不会出现在结果中。</p>
     *
     * @param workspaceId 工作区标识
     * @param documentIds 待查询文档 ID 集合
     * @return 以 documentId 为键的版本事实映射
     */
    Map<String, AskableDocumentVersion> findAskableVersions(String workspaceId, Collection<String> documentIds);

    /**
     * 查询当前用户在指定知识库内可进入问答召回的文档版本集合。
     *
     * <p>该方法同时应用文档版本状态与授权边界：只返回指定用户有权问答的文档，
     * 且每个文档只返回最近一个已 INDEXED 版本。</p>
     *
     * @param currentUser 当前用户上下文
     * @param kbId 知识库 ID
     * @return 可召回的文档版本集合
     */
    List<AskableDocumentVersion> findAskableVersionsForQuestion(CurrentUser currentUser, String kbId);
}
