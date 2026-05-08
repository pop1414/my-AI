package io.github.spike.myai.ingest.domain.model;

import io.github.spike.myai.shared.workspace.WorkspaceConstants;

/**
 * 文档列表读模型过滤条件（Domain Value Object）。
 *
 * <p>该 Record 用于封装文档列表查询所需的筛选与分页参数，
 * 避免仓储接口暴露过长的方法签名。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@code kbId} —— 知识库 ID 精确过滤（{@code null} 表示不过滤）；</li>
 *   <li>{@code status} —— 文档状态精确过滤（{@code null} 表示不过滤）；</li>
 *   <li>{@code filename} —— 文件名模糊匹配关键字（{@code null} 表示不过滤）；</li>
 *   <li>{@code excludeDeleted} —— 是否排除已删除文档（{@code true} 时
 *       SQL 层追加 {@code status <> 'DELETED'} 条件）；</li>
 *   <li>{@code limit} —— 每页条数（正整数）；</li>
 *   <li>{@code offset} —— 偏移量（非负整数）。</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <p>该对象为值对象（不可变），由应用层组装后传入仓储端口，
 * 仓储实现负责将其转换为 SQL WHERE / LIMIT / OFFSET 子句。
 *
 * @param workspaceId     工作区标识（当前固定为 {@code "default"}）
 * @param kbId           知识库 ID（可为 {@code null}）
 * @param status         文档状态（可为 {@code null}）
 * @param filename       文件名关键字（可为 {@code null}）
 * @param excludeDeleted 是否排除已删除文档
 * @param limit          每页条数
 * @param offset         偏移量
 * @author Spike
 * @since 1.0.0
 */
public record DocumentListFilter(
        String workspaceId,
        String kbId,
        UploadStatus status,
        String filename,
        boolean excludeDeleted,
        int limit,
        int offset) {

    /**
     * 便利构造器：自动填充默认工作区标识。
     *
     * <p>当前为单工作区模式，调用方无需显式传递 {@code workspaceId}，
     * 该构造器自动使用 {@link WorkspaceConstants#DEFAULT_WORKSPACE_ID}。
     *
     * @param kbId           知识库 ID（可为 {@code null}）
     * @param status         文档状态（可为 {@code null}）
     * @param filename       文件名关键字（可为 {@code null}）
     * @param excludeDeleted 是否排除已删除文档
     * @param limit          每页条数
     * @param offset         偏移量
     */
    public DocumentListFilter(
            String kbId,
            UploadStatus status,
            String filename,
            boolean excludeDeleted,
            int limit,
            int offset) {
        this(WorkspaceConstants.DEFAULT_WORKSPACE_ID, kbId, status, filename, excludeDeleted, limit, offset);
    }

    /**
     * 紧凑构造器：执行分页参数的基本合法性校验。
     *
     * <p>此处仅校验分页参数，筛选字段的格式校验已在上游
     * {@code ListDocumentsQuery} 中完成，不在领域层重复。
     *
     * @throws IllegalArgumentException 当分页参数不合法时
     */
    public DocumentListFilter {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        // limit 必须为正整数，防止 SQL 中 LIMIT 0 或负值
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        // offset 必须为非负整数，防止 SQL 中 OFFSET 负值
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }
}
