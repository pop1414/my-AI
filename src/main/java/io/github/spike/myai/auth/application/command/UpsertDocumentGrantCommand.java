package io.github.spike.myai.auth.application.command;

import io.github.spike.myai.auth.domain.model.DocumentPermission;

/**
 * 授予或更新文档授权命令。
 * <p>
 * 封装 Upsert 文档授权操作所需的全部入参（文档 ID + 用户 ID + 目标权限），
 * 在构造阶段完成非空及非空白校验，并提供规范化和权限解析方法供下游用例层使用。
 * 设计为不可变 Record，适合在分层架构中跨层传递。
 *
 * @param documentId 文档唯一标识，不能为空或纯空白
 * @param userId     目标用户唯一标识，不能为空或纯空白
 * @param permission 目标文档权限字符串，不能为空或纯空白，需可解析为 {@link DocumentPermission} 枚举
 * @author spike
 * @since 1.0.0
 */
public record UpsertDocumentGrantCommand(
        String documentId,
        String userId,
        String permission) {

    /**
     * 紧凑构造器：在 Record 构造阶段校验三个必填字段。
     */
    public UpsertDocumentGrantCommand {
        // 校验文档 ID、用户 ID、权限均非空且非纯空白
        documentId = requireText(documentId, "documentId is required");
        userId = requireText(userId, "userId is required");
        permission = requireText(permission, "permission is required");
    }

    /**
     * 获取去除首尾空白后的文档 ID。
     *
     * @return 规范化后的文档 ID
     */
    public String normalizedDocumentId() {
        return documentId.trim();
    }

    /**
     * 获取去除首尾空白后的用户 ID。
     *
     * @return 规范化后的用户 ID
     */
    public String normalizedUserId() {
        return userId.trim();
    }

    /**
     * 将权限字符串解析为 {@link DocumentPermission} 枚举值。
     * <p>
     * 解析前先进行 trim 处理，若字符串无法匹配任何枚举常量，
     * 则抛出携带原始输入值的 {@link IllegalArgumentException}，便于调用方定位问题。
     *
     * @return 对应的 {@link DocumentPermission} 枚举值
     * @throws IllegalArgumentException 当权限字符串无法解析为有效枚举值时抛出
     */
    public DocumentPermission resolvedPermission() {
        try {
            // trim 后尝试匹配枚举常量
            return DocumentPermission.valueOf(permission.trim());
        } catch (IllegalArgumentException ex) {
            // 保留原始输入值以便排查，包装后重新抛出
            throw new IllegalArgumentException("invalid documentPermission: " + permission, ex);
        }
    }

    /**
     * 通用非空文本校验方法。
     *
     * @param value   待校验值
     * @param message 校验失败时的错误消息
     * @return 原值
     * @throws IllegalArgumentException 当值为 {@code null} 或纯空白时抛出
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
