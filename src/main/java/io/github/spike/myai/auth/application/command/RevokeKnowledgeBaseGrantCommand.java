package io.github.spike.myai.auth.application.command;

/**
 * 回收知识库授权命令。
 * <p>
 * 封装回收授权操作所需的全部入参（知识库 ID + 用户 ID），
 * 在构造阶段完成非空及非空白校验，并提供规范化方法供下游用例层使用。
 * 设计为不可变 Record，适合在分层架构中跨层传递。
 *
 * @param kbId   知识库唯一标识，不能为空或纯空白
 * @param userId 目标用户唯一标识，不能为空或纯空白
 * @author spike
 * @since 1.0.0
 */
public record RevokeKnowledgeBaseGrantCommand(
        String kbId,
        String userId) {

    /**
     * 紧凑构造器：在 Record 构造阶段校验必填字段。
     */
    public RevokeKnowledgeBaseGrantCommand {
        // 校验知识库 ID 非空且非纯空白
        kbId = requireText(kbId, "kbId is required");
        // 校验用户 ID 非空且非纯空白
        userId = requireText(userId, "userId is required");
    }

    /**
     * 获取去除首尾空白后的知识库 ID，用于仓储层精确匹配。
     *
     * @return 规范化（trim）后的知识库 ID
     */
    public String normalizedKbId() {
        return kbId.trim();
    }

    /**
     * 获取去除首尾空白后的用户 ID，用于仓储层精确匹配。
     *
     * @return 规范化（trim）后的用户 ID
     */
    public String normalizedUserId() {
        return userId.trim();
    }

    /**
     * 通用非空文本校验方法。
     *
     * @param value   待校验值
     * @param message 校验失败时的错误消息
     * @return 原值（校验通过时返回，供紧凑构造器赋值）
     * @throws IllegalArgumentException 当值为 {@code null} 或纯空白时抛出
     */
    private static String requireText(String value, String message) {
        // null 或 trim 后为空均视为非法输入
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
