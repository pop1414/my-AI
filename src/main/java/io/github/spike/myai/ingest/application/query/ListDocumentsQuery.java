package io.github.spike.myai.ingest.application.query;

import io.github.spike.myai.ingest.domain.model.UploadStatus;
import java.util.Set;

/**
 * 文档列表查询对象（应用层 Query DTO）。
 *
 * <p>该 Record 负责承接接口层（Controller）传入的筛选与分页参数，
 * 并在应用层边界完成基础合法性校验，确保进入领域层的数据始终有效。
 *
 * <h3>职责</h3>
 * <ol>
 *   <li><b>参数校验</b>：在紧凑构造器中校验 limit/offset/filename/status 的合法性；</li>
 *   <li><b>数据规整化</b>：通过 {@code normalizedXxx} 系列方法将空白字符串转为
 *       {@code null}，便于后续 SQL 动态条件拼接；</li>
 *   <li><b>业务语义转换</b>：将字符串状态转为枚举，并提供"默认排除已删除"的语义。</li>
 * </ol>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code limit}：1~100；</li>
 *   <li>{@code offset}：≥0；</li>
 *   <li>{@code filename}：最长 200 字符；</li>
 *   <li>{@code status}：必须为支持的枚举值之一（含 DELETED），
 *       未传时不校验且默认排除 DELETED。</li>
 * </ul>
 *
 * @param kbId     知识库 ID（可选，为空时不过滤）
 * @param status   文档状态过滤（可选，支持 UPLOADED/INGESTING/INDEXED/FAILED/DELETING/DELETED）
 * @param filename 文件名模糊匹配关键字（可选，最长 200 字符）
 * @param limit    每页条数（1~100）
 * @param offset   偏移量（≥0）
 * @author Spike
 * @since 1.0.0
 */
public record ListDocumentsQuery(
        String kbId,
        String status,
        String filename,
        int limit,
        int offset) {

    /** 文件名模糊匹配关键字的最大长度 */
    private static final int MAX_FILENAME_LENGTH = 200;

    /** 支持的状态枚举值集合，用于快速校验 status 参数合法性 */
    private static final Set<String> SUPPORTED_STATUSES = Set.of(
            UploadStatus.UPLOADED.name(),
            UploadStatus.INGESTING.name(),
            UploadStatus.INDEXED.name(),
            UploadStatus.FAILED.name(),
            UploadStatus.DELETING.name(),
            UploadStatus.DELETED.name());

    /**
     * 紧凑构造器：在对象创建时执行参数校验与数据规整化。
     *
     * @throws IllegalArgumentException 当参数不满足校验规则时
     */
    public ListDocumentsQuery {
        // 1. limit 范围校验
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        // 2. offset 非负校验
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be greater than or equal to 0");
        }
        // 3. filename 长度校验（仅当传入时）
        if (filename != null && filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("filename length must be less than or equal to 200");
        }

        // 4. status 枚举值校验：空白视为未传，非空则必须为合法状态
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null && !SUPPORTED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException(
                    "status must be one of UPLOADED, INGESTING, INDEXED, FAILED, DELETING, DELETED");
        }
    }

    /**
     * 返回规范化后的知识库标识。
     *
     * <p>将空白字符串转为 {@code null}，便于 SQL 动态条件判断"是否传入 kbId"。
     *
     * @return 规范化后的 kbId，可能为 {@code null}
     */
    public String normalizedKbId() {
        return blankToNull(kbId);
    }

    /**
     * 返回规范化后的文件名模糊匹配关键字。
     *
     * <p>将空白字符串转为 {@code null}，便于 SQL 动态条件判断"是否需要 LIKE 过滤"。
     *
     * @return 规范化后的文件名关键字，可能为 {@code null}
     */
    public String normalizedFilename() {
        return blankToNull(filename);
    }

    /**
     * 返回显式指定的文档状态；若未指定则返回 {@code null}。
     *
     * @return 请求的状态枚举值，可能为 {@code null}
     */
    public UploadStatus requestedStatus() {
        String normalizedStatus = blankToNull(status);
        if (normalizedStatus == null) {
            return null;
        }
        return UploadStatus.valueOf(normalizedStatus);
    }

    /**
     * 判断是否应默认排除已删除文档。
     *
     * <p>逻辑：当用户未显式指定状态时，自动排除 {@code DELETED} 状态的文档，
     * 使默认列表不展示已删除的文档，保持界面整洁。
     *
     * @return {@code true} 表示应排除已删除文档
     */
    public boolean excludeDeletedByDefault() {
        return requestedStatus() == null;
    }

    /**
     * 将空白字符串转为 {@code null}。
     *
     * <p>该方法统一处理"用户未传参数"与"传了空字符串"两种情况，
     * 使后续 SQL 动态条件判断逻辑简洁（直接判 {@code null} 即可）。
     *
     * @param value 原始字符串值
     * @return 非空时返回原值，为空/空白时返回 {@code null}
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
