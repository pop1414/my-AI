package io.github.spike.myai.knowledge.application.command;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;

/**
 * 创建知识库命令（应用层 DTO）。
 *
 * <p>该 Record 作为应用层用例的入参载体，承担两项职责：
 * <ol>
 *   <li><b>参数校验</b>：在紧凑构造器中完成输入合法性检查，
 *       确保进入领域层的数据始终有效（符合阿里巴巴规范中
 *       "参数校验在接口层完成"的原则）；</li>
 *   <li><b>数据规整化</b>：通过 {@code normalizedXxx / resolvedXxx}
 *       系列方法，将原始输入转换为领域层可直接使用的标准形式，
 *       消除空值、前后空格等边界情况。</li>
 * </ol>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code name}：必填，去除首尾空格后长度 1~100 字符；</li>
 *   <li>{@code description}：选填，去除首尾空格后最长 500 字符，
 *       未传时自动规整为空字符串；</li>
 *   <li>{@code status}：选填，未传时默认 {@link KnowledgeBaseStatus#ACTIVE}。</li>
 * </ul>
 *
 * @param name        知识库名称（必填，1~100 字符）
 * @param description 知识库描述（选填，最长 500 字符，可为 {@code null}）
 * @param status      知识库状态（选填，默认 {@code ACTIVE}）
 * @author Spike
 * @since 1.0.0
 */
public record CreateKnowledgeBaseCommand(
        String name,
        String description,
        KnowledgeBaseStatus status) {

    /**
     * 紧凑构造器（Compact Canonical Constructor）。
     *
     * <p>在 Record 对象构造时自动执行参数校验与数据规整化，
     * 确保对象创建后即处于一致有效状态（不可变 + 已验证）。
     *
     * <p>校验顺序遵循"先必填后选填、先空值后长度"原则，
     * 使错误信息精准定位第一个违规字段。
     *
     * @throws IllegalArgumentException 当参数不满足校验规则时
     */
    public CreateKnowledgeBaseCommand {
        // 1. 名称必填校验：不允许为 null 或空白字符串
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // 2. 名称长度校验：去除首尾空格后不超过 100 字符
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("name length must be between 1 and 100");
        }

        // 3. 描述规整化与长度校验
        //    null 视为空字符串，去除首尾空格；最长 500 字符
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.length() > 500) {
            throw new IllegalArgumentException("description length must be between 0 and 500");
        }
        // 将规整化后的描述写回字段，后续方法可直接使用
        description = normalizedDescription;
    }

    /**
     * 返回规整化后的知识库名称（去除首尾空格）。
     *
     * <p>调用方无需再次处理空值或空格，可直接用于领域模型构造。
     *
     * @return 去除首尾空格后的名称，保证非空
     */
    public String normalizedName() {
        return name.trim();
    }

    /**
     * 返回规整化后的知识库描述。
     *
     * <p>{@code null} 会被转换为空字符串，调用方无需判空。
     *
     * @return 去除首尾空格后的描述，保证非 {@code null}（可能为空字符串）
     */
    public String normalizedDescription() {
        return description == null ? "" : description.trim();
    }

    /**
     * 返回解析后的知识库状态，未传时默认返回 {@link KnowledgeBaseStatus#ACTIVE}。
     *
     * @return 有效的状态枚举值，保证非 {@code null}
     */
    public KnowledgeBaseStatus resolvedStatus() {
        return status == null ? KnowledgeBaseStatus.ACTIVE : status;
    }
}
