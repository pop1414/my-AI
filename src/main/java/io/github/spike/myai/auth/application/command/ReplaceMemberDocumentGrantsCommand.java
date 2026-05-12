package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.DocumentPermission;
import java.util.List;

/**
 * 替换成员文档授权命令。
 * <p>
 * 用于一次性全量替换指定用户在所有文档上的权限分配。
 * 该命令以用户ID为主视角，接收一组文档-权限分配列表，
 * 执行后将覆盖该用户已有的所有文档权限。
 * </p>
 *
 * @param userId      目标用户的唯一标识，不可为空
 * @param assignments 授权分配列表，包含文档ID与对应权限；为 null 时视为空列表
 * @author spike
 */
public record ReplaceMemberDocumentGrantsCommand(
        String userId,
        List<Assignment> assignments) {

    /**
     * 紧凑构造器，用于参数校验与防御性拷贝。
     * <ul>
     *   <li>校验 {@code userId} 不为空；</li>
     *   <li>若 {@code assignments} 为 {@code null}，则初始化为不可变空列表；</li>
     *   <li>否则进行不可变拷贝，防止外部修改。</li>
     * </ul>
     */
    public ReplaceMemberDocumentGrantsCommand {
        userId = requireText(userId, "userId is required");
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
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
     * 授权分配记录，表示用户在一个文档上的权限。
     *
     * @param documentId 文档唯一标识
     * @param permission 文档权限字符串，需可解析为 {@link DocumentPermission}
     */
    public record Assignment(String documentId, String permission) {
        /**
         * 紧凑构造器，校验文档ID和权限字符串均不为空。
         */
        public Assignment {
            documentId = requireText(documentId, "documentId is required");
            permission = requireText(permission, "permission is required");
        }

        /**
         * 获取去除首尾空白后的文档ID。
         *
         * @return 规范化后的文档ID
         */
        public String normalizedDocumentId() {
            return documentId.trim();
        }

        /**
         * 将权限字符串解析为 {@link DocumentPermission} 枚举。
         * <p>
         * 首先去除权限字符串首尾空白，再通过 {@code valueOf} 转换为枚举值。
         * 若字符串无法匹配任何枚举常量，则抛出异常并附带原始输入信息。
         * </p>
         *
         * @return 解析后的文档权限枚举
         * @throws IllegalArgumentException 当权限字符串无法解析为有效的 {@link DocumentPermission} 时
         */
        public DocumentPermission resolvedPermission() {
            try {
                return DocumentPermission.valueOf(permission.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid documentPermission: " + permission, ex);
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
