package io.github.spike.myai.auth.interfaces.rest.dto;

import java.util.List;

/**
 * 创建工作区成员请求体。
 *
 * @param username 用户名
 * @param displayName 展示名称
 * @param password 初始密码
 * @param initialKnowledgeBaseGrants 初始知识库授权
 */
public record CreateManagedMemberRequest(
        String username,
        String displayName,
        String password,
        List<InitialKnowledgeBaseGrantRequest> initialKnowledgeBaseGrants) {

    public record InitialKnowledgeBaseGrantRequest(String kbId, String role) {
    }
}
