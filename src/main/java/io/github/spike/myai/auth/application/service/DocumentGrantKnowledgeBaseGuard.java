package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import org.springframework.stereotype.Component;

/**
 * 文档授权父级知识库授权守卫。
 *
 * <p>文档是知识库下的子资源，成员只有先拥有对应知识库的有效授权，
 * 才能被授予该知识库下文档的显式权限覆盖。该守卫用于统一拦截
 * 单条授权和批量授权入口，避免产生没有父级知识库授权的孤儿文档授权。
 */
@Component
public class DocumentGrantKnowledgeBaseGuard {

    /** 知识库授权治理仓储，用于检查目标成员的父级授权是否存在 */
    private final KnowledgeBaseGrantManagementRepository knowledgeBaseGrantRepository;

    /**
     * 构造器注入知识库授权仓储。
     *
     * @param knowledgeBaseGrantRepository 知识库授权治理仓储
     */
    public DocumentGrantKnowledgeBaseGuard(KnowledgeBaseGrantManagementRepository knowledgeBaseGrantRepository) {
        this.knowledgeBaseGrantRepository = knowledgeBaseGrantRepository;
    }

    /**
     * 要求目标成员已拥有文档所属知识库的有效授权。
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标成员 ID
     * @param kbId        文档所属知识库 ID
     * @throws IllegalArgumentException 当目标成员没有该知识库有效授权时抛出
     */
    public void requireMemberKnowledgeBaseGrant(String workspaceId, String userId, String kbId) {
        if (!hasMemberKnowledgeBaseGrant(workspaceId, userId, kbId)) {
            throw new IllegalArgumentException(
                    "document grant requires active knowledge base grant: " + kbId);
        }
    }

    /**
     * 判断目标成员是否拥有指定知识库的有效授权。
     *
     * @param workspaceId 工作区 ID
     * @param userId      目标成员 ID
     * @param kbId        知识库 ID
     * @return {@code true} 表示存在有效知识库授权；否则返回 {@code false}
     */
    public boolean hasMemberKnowledgeBaseGrant(String workspaceId, String userId, String kbId) {
        return knowledgeBaseGrantRepository.findActiveGrant(workspaceId, kbId, userId).isPresent();
    }
}
