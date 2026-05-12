package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import java.util.List;

/**
 * 创建工作区成员并初始化知识库授权命令。
 *
 * <p>与 {@link io.github.spike.myai.auth.application.command.CreateManagedAccountCommand} 不同，
 * 本命令限定目标角色为 WORKSPACE_MEMBER，且要求至少提供一个初始知识库授权，
 * 实现"开户即授权"的一站式成员创建体验。
 *
 * @param username                    用户名
 * @param displayName                 展示名称
 * @param password                    明文密码（由应用服务负责编码）
 * @param initialKnowledgeBaseGrants  初始知识库授权集合，不可为空
 */
public record CreateManagedMemberCommand(
        String username,
        String displayName,
        String password,
        List<InitialKnowledgeBaseGrantCommand> initialKnowledgeBaseGrants) {

    /**
     * 紧凑构造函数，对所有字段做非空校验，并将授权列表做不可变拷贝。
     */
    public CreateManagedMemberCommand {
        username = requireText(username, "username is required");
        displayName = requireText(displayName, "displayName is required");
        password = requireText(password, "password is required");
        // 初始知识库授权不可为空，保证成员创建后立即可用
        if (initialKnowledgeBaseGrants == null || initialKnowledgeBaseGrants.isEmpty()) {
            throw new IllegalArgumentException("initialKnowledgeBaseGrants is required");
        }
        // 防御性不可变拷贝，防止外部修改
        initialKnowledgeBaseGrants = List.copyOf(initialKnowledgeBaseGrants);
    }

    /**
     * 返回去除前后空白后的用户名。
     *
     * @return 规范化用户名
     */
    public String normalizedUsername() {
        return username.trim();
    }

    /**
     * 返回去除前后空白后的展示名称。
     *
     * @return 规范化展示名称
     */
    public String normalizedDisplayName() {
        return displayName.trim();
    }

    /**
     * 返回去除前后空白后的明文密码。
     *
     * @return 规范化密码
     */
    public String normalizedPassword() {
        return password.trim();
    }

    /**
     * 返回初始知识库授权列表的不可变视图。
     *
     * <p>该列表在紧凑构造函数中已经过 {@link List#copyOf} 处理，
     * 调用方无法通过此方法修改原始授权数据。
     *
     * @return 初始知识库授权命令列表
     */
    public List<InitialKnowledgeBaseGrantCommand> initialKnowledgeBaseGrants() {
        return initialKnowledgeBaseGrants;
    }

    /**
     * 初始知识库授权命令内嵌记录。
     *
     * <p>每一条记录描述一个知识库的初始角色分配。
     *
     * @param kbId 知识库 ID
     * @param role 知识库角色字符串（如 KB_READER、KB_CONTRIBUTOR）
     */
    public record InitialKnowledgeBaseGrantCommand(String kbId, String role) {
        /**
         * 紧凑构造函数，对 kbId 和 role 做非空校验。
         */
        public InitialKnowledgeBaseGrantCommand {
            kbId = requireText(kbId, "kbId is required");
            role = requireText(role, "role is required");
        }

        /**
         * 返回去除前后空白后的知识库 ID。
         *
         * @return 规范化知识库 ID
         */
        public String normalizedKbId() {
            return kbId.trim();
        }

        /**
         * 将角色字符串解析为 {@link KnowledgeBaseRole} 枚举。
         *
         * @return 解析后的知识库角色
         * @throws IllegalArgumentException 如果角色字符串不是合法的枚举值
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
     * 校验字符串不能为空（null 或纯空白）。
     *
     * @param value   待校验字符串
     * @param message 校验失败时的异常消息
     * @return 原始字符串
     * @throws IllegalArgumentException 如果 value 为 null 或纯空白
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
