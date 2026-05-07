package io.github.spike.myai.knowledge.application.command;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;

/**
 * 编辑（更新）知识库命令（应用层 DTO）。
 *
 * <p>该 Record 作为更新知识库用例的入参载体，与
 * {@link CreateKnowledgeBaseCommand} 的关键区别在于：
 * <ul>
 *   <li>增加了 {@code kbId} 字段用于定位目标知识库；</li>
 *   <li>{@code name} / {@code description} / {@code status} 均允许为
 *       {@code null}，表示"不修改该字段"；</li>
 *   <li>至少需要传入一个可更新字段，否则视为无效请求。</li>
 * </ul>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code kbId}：必填，不能为空白字符串；</li>
 *   <li>{@code name}：选填，若传入则去除首尾空格后长度须在 1~100 字符；</li>
 *   <li>{@code description}：选填，若传入则去除首尾空格后最长 500 字符；</li>
 *   <li>{@code status}：选填，无额外校验；</li>
 *   <li>至少有一个业务字段（name / description / status）不为 {@code null}。</li>
 * </ul>
 *
 * @param kbId        目标知识库业务键（必填）
 * @param name        更新后的名称（选填，{@code null} 表示不修改）
 * @param description 更新后的描述（选填，{@code null} 表示不修改；传空字符串表示清空）
 * @param status      更新后的状态（选填，{@code null} 表示不修改）
 * @author Spike
 * @since 1.0.0
 */
public record UpdateKnowledgeBaseCommand(
        String kbId,
        String name,
        String description,
        KnowledgeBaseStatus status) {

    /**
     * 紧凑构造器（Compact Canonical Constructor）。
     *
     * <p>在 Record 对象构造时自动执行参数校验。
     * 校验逻辑与创建命令的关键差异：
     * <ul>
     *   <li>{@code name} / {@code description} / {@code status} 允许为
     *       {@code null}（语义为"不修改"），仅当显式传入时才进行格式校验；</li>
     *   <li>引入"至少一个字段非空"的兜底校验，防止空更新请求穿透到领域层。</li>
     * </ul>
     *
     * @throws IllegalArgumentException 当参数不满足校验规则时
     */
    public UpdateKnowledgeBaseCommand {
        // 1. kbId 必填校验：定位目标知识库的唯一标识，不可为空
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }

        // 2. name 条件校验：仅当显式传入（非 null）时才进行空值和长度校验
        //    null 语义为"不修改该字段"，与传空字符串含义不同
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (name.trim().length() > 100) {
                throw new IllegalArgumentException("name length must be between 1 and 100");
            }
        }

        // 3. description 条件校验：仅当显式传入（非 null）时才进行长度校验
        //    null 表示不修改；空字符串表示清空描述（合法操作）
        if (description != null && description.trim().length() > 500) {
            throw new IllegalArgumentException("description length must be between 0 and 500");
        }

        // 4. 兜底校验：至少需要传入一个可更新字段，防止空更新请求
        //    三个业务字段全为 null 时，请求无实际意义，应尽早拒绝
        if (name == null && description == null && status == null) {
            throw new IllegalArgumentException("at least one field must be provided");
        }
    }

    /**
     * 返回规整化后的知识库业务键（去除首尾空格）。
     *
     * @return 去除首尾空格后的 kbId，保证非空
     */
    public String normalizedKbId() {
        return kbId.trim();
    }

    /**
     * 返回规整化后的名称，或当名称为 {@code null} 时回退到指定默认值。
     *
     * <p>该方法实现了"传了则改、不传则保持原值"的更新语义：
     * 当 {@code name} 为 {@code null} 时返回 {@code fallback}
     * （即当前知识库的名称），否则返回去除首尾空格后的新名称。
     *
     * @param fallback 当名称为 {@code null} 时的回退值（通常为当前实体名称）
     * @return 规整化后的名称（可能为 {@code fallback}）
     */
    public String normalizedNameOrDefault(String fallback) {
        return name == null ? fallback : name.trim();
    }

    /**
     * 返回规整化后的描述，或当描述为 {@code null} 时回退到指定默认值。
     *
     * <p>与 {@link #normalizedNameOrDefault(String)} 语义一致：
     * {@code null} 表示不修改，返回 {@code fallback}；
     * 非 {@code null} 时返回去除首尾空格后的新描述（含空字符串）。
     *
     * @param fallback 当描述为 {@code null} 时的回退值（通常为当前实体描述）
     * @return 规整化后的描述（可能为 {@code fallback}，可能为空字符串）
     */
    public String normalizedDescriptionOrDefault(String fallback) {
        return description == null ? fallback : description.trim();
    }

    /**
     * 返回解析后的状态，或当状态为 {@code null} 时回退到指定默认值。
     *
     * <p>与名称/描述的回退逻辑一致：{@code null} 语义为"不修改状态"，
     * 返回 {@code fallback}（即当前知识库的状态）。
     *
     * @param fallback 当状态为 {@code null} 时的回退值（通常为当前实体状态）
     * @return 有效的状态枚举值（可能为 {@code fallback}）
     */
    public KnowledgeBaseStatus resolvedStatusOrDefault(KnowledgeBaseStatus fallback) {
        return status == null ? fallback : status;
    }
}
