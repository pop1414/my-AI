package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import java.util.List;

/**
 * 替换知识库成员授权命令。
 * <p>
 * 用于一次性全量替换指定知识库的所有成员授权关系。
 * 该命令接收一个知识库ID和一组用户-角色分配列表，
 * 执行后将覆盖该知识库下已有的所有成员角色。
 * </p>
 *
 * @param kbId        目标知识库的唯一标识，不可为空
 * @param assignments 授权分配列表，包含用户ID与对应角色；为 null 时视为空列表
 * @author spike
 */
public record ReplaceKnowledgeBaseMemberGrantsCommand(
        String kbId,
        List<Assignment> assignments) {

    /**
     * 紧凑构造器，用于参数校验与防御性拷贝。
     * <ul>
     *   <li>校验 {@code kbId} 不为空；</li>
     *   <li>若 {@code assignments} 为 {@code null}，则初始化为不可变空列表；</li>
     *   <li>否则进行不可变拷贝，防止外部修改。</li>
     * </ul>
     */
    public ReplaceKnowledgeBaseMemberGrantsCommand {
        kbId = requireText(kbId, "kbId is required");
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
    }

    /**
     * 获取去除首尾空白后的知识库ID。
     *
     * @return 规范化后的知识库ID
     */
    public String normalizedKbId() {
        return kbId.trim();
    }

    /**
     * 授权分配记录，表示一名用户在知识库中的角色。
     *
     * @param userId 用户唯一标识
     * @param role   知识库角色字符串，需可解析为 {@link KnowledgeBaseRole}
     */
    public record Assignment(String userId, String role) {
        /**
         * 紧凑构造器，校验用户ID和角色字符串均不为空。
         */
        public Assignment {
            userId = requireText(userId, "userId is required");
            role = requireText(role, "role is required");
        }

        /**
         * 获取去除首尾空白后的用户ID。
         *
         * @return 规范化后的用户ID
         */
        public String normalizedUserId() {
            return userId.trim();
        }

        /**
         * 将角色字符串解析为 {@link KnowledgeBaseRole} 枚举。
         * <p>
         * 首先去除角色字符串首尾空白，再通过 {@code valueOf} 转换为枚举值。
         * 若字符串无法匹配任何枚举常量，则抛出异常并附带原始输入信息。
         * </p>
         *
         * @return 解析后的知识库角色枚举
         * @throws IllegalArgumentException 当角色字符串无法解析为有效的 {@link KnowledgeBaseRole} 时
         */
        public KnowledgeBaseRole resolvedRole() {
            try {
                return KnowledgeBaseRole.valueOf(role.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid knowledgeBaseRole: " + role, ex);
            }
        }
    }

    /**
     * 校验字符串非空（不为 {@code null} 且去除空白后非空字符串）。
     *
     * @param value   待校验的字符串
     * @param message 校验失败时的异常消息
     * @return 原始字符串（校验通过时原样返回）
     * @throws IllegalArgumentException 当 {@code value} 为 {@code null} 或空白字符串时
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
