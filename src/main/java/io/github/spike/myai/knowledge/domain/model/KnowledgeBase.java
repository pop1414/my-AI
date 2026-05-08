package io.github.spike.myai.knowledge.domain.model;

import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;

/**
 * 知识库聚合根（Aggregate Root）。
 *
 * <p>该类是知识库限界上下文的核心领域模型，承担以下职责：
 * <ol>
 *   <li><b>不变性约束</b>：在紧凑构造器中强制校验所有必填字段与格式规则，
 *       确保聚合根在创建后即处于一致有效状态；</li>
 *   <li><b>生命周期管理</b>：通过静态工厂 {@link #create} 创建新聚合，
 *       通过实例方法 {@link #update} 生成更新后的新实例（不可变模式）；</li>
 *   <li><b>业务规则封装</b>：所有与知识库实体相关的业务判断均内聚于此，
 *       应用层仅负责流程编排。</li>
 * </ol>
 *
 * <h3>设计决策</h3>
 * <ul>
 *   <li>使用 Java {@code record} 实现不可变性，由编译器自动生成
 *       {@code equals / hashCode / toString}；</li>
 *   <li>{@code update} 方法返回新实例而非修改自身，
 *       确保线程安全与审计追踪；</li>
 *   <li>{@code createdAt} 在创建时固化，{@code updatedAt} 在每次更新时刷新。</li>
 * </ul>
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code kbId}：必填，不可为空白；</li>
 *   <li>{@code name}：必填，去除首尾空格后长度 1~100 字符；</li>
 *   <li>{@code description}：选填，去除首尾空格后最长 500 字符，
 *       {@code null} 规整为空字符串；</li>
 *   <li>{@code status}：必填，不可为 {@code null}；</li>
 *   <li>{@code createdAt / updatedAt}：必填，不可为 {@code null}。</li>
 * </ul>
 *
 * @param kbId        对外业务键（系统生成的全局唯一标识）
 * @param workspaceId 所属工作区标识（当前固定为 {@code "default"}）
 * @param name        展示名称（1~100 字符）
 * @param description 描述信息（可为空字符串，最长 500 字符）
 * @param status      当前生命周期状态
 * @param createdAt   创建时间（不可变）
 * @param updatedAt   最后更新时间
 * @author Spike
 * @since 1.0.0
 */
public record KnowledgeBase(
        String kbId,
        String workspaceId,
        String name,
        String description,
        KnowledgeBaseStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 便利构造器：自动填充默认工作区标识。
     *
     * <p>当前为单工作区模式，调用方无需显式传递 {@code workspaceId}，
     * 该构造器自动使用 {@link WorkspaceConstants#DEFAULT_WORKSPACE_ID}。
     *
     * @param kbId        知识库业务键
     * @param name        展示名称
     * @param description 描述信息
     * @param status      生命周期状态
     * @param createdAt   创建时间
     * @param updatedAt   更新时间
     */
    public KnowledgeBase(
            String kbId,
            String name,
            String description,
            KnowledgeBaseStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this(kbId, WorkspaceConstants.DEFAULT_WORKSPACE_ID, name, description, status, createdAt, updatedAt);
    }

    /**
     * 紧凑构造器（Compact Canonical Constructor）。
     *
     * <p>在对象创建时强制执行所有不变性约束。
     * 校验顺序遵循"先必填后选填、先空值后长度"原则，
     * 确保首个违规就能得到精准的错误提示。
     *
     * <p>注意：Record 紧凑构造器中允许对字段重新赋值
     * （如 {@code name} 去除空格、{@code description} 规整化），
     * 这是 Java 语言规范明确允许的行为。
     *
     * @throws IllegalArgumentException 当任一字段不满足约束时
     */
    public KnowledgeBase {
        // 1. kbId 必填校验：聚合根标识不可为空
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }

        // 2. workspaceId 必填校验：工作区标识不可为空（便利构造器自动填充默认值）
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }

        // 3. name 必填校验：名称不可为空或空白字符串
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // 4. name 长度校验：去除首尾空格后不超过 100 字符
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("name length must be between 1 and 100");
        }

        // 5. description 规整化与长度校验
        //    null 视为空字符串，去除首尾空格；最长 500 字符
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalizedDescription.length() > 500) {
            throw new IllegalArgumentException("description length must be between 0 and 500");
        }
        description = normalizedDescription;    // 写回规整化后的值

        // 6. status 必填校验：聚合根必须有一个明确的状态
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }

        // 7. 时间戳必填校验：创建时间和更新时间均不可为空
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("createdAt/updatedAt must not be null");
        }

        // 8. name 去除首尾空格后写回（在长度校验通过后执行）
        name = name.trim();
    }

    /**
     * 静态工厂方法：创建新的知识库聚合根（使用默认工作区）。
     *
     * <p>便利重载，自动填充 {@link WorkspaceConstants#DEFAULT_WORKSPACE_ID}。
     * 新创建时 {@code createdAt} 与 {@code updatedAt} 均为同一时间点，
     * 表示"创建即最后一次修改"。
     *
     * @param kbId        知识库业务键
     * @param name        知识库名称
     * @param description 知识库描述
     * @param status      初始状态
     * @param now         当前时间（创建时间）
     * @return 新创建的知识库聚合根
     */
    public static KnowledgeBase create(
            String kbId,
            String name,
            String description,
            KnowledgeBaseStatus status,
            Instant now) {
        return create(kbId, WorkspaceConstants.DEFAULT_WORKSPACE_ID, name, description, status, now);
    }

    /**
     * 静态工厂方法：创建新的知识库聚合根（显式指定工作区）。
     *
     * <p>新创建时 {@code createdAt} 与 {@code updatedAt} 均为同一时间点。
     * 调用方需显式传入 {@code workspaceId}，适用于多工作区场景。
     *
     * @param kbId        知识库业务键
     * @param workspaceId 所属工作区标识
     * @param name        知识库名称
     * @param description 知识库描述
     * @param status      初始状态
     * @param now         当前时间（创建时间）
     * @return 新创建的知识库聚合根
     */
    public static KnowledgeBase create(
            String kbId,
            String workspaceId,
            String name,
            String description,
            KnowledgeBaseStatus status,
            Instant now) {
        // 创建时 createdAt 与 updatedAt 设为同一时间
        return new KnowledgeBase(kbId, workspaceId, name, description, status, now, now);
    }

    /**
     * 生成更新后的知识库聚合根（不可变模式）。
     *
     * <p>该方法不修改当前实例，而是以当前实例的 {@code kbId} 和
     * {@code createdAt} 为基础，结合传入的新字段值与新时间戳，
     * 构造一个全新的 {@link KnowledgeBase} 实例。
     *
     * <p>设计意图：
     * <ul>
     *   <li>保持领域模型的不可变性，避免副作用；</li>
     *   <li>{@code createdAt} 始终不变，{@code updatedAt} 刷新为当前时间；</li>
     *   <li>新实例自动经过紧凑构造器校验，确保更新后依然合法。</li>
     * </ul>
     *
     * @param nextName        更新后的名称
     * @param nextDescription 更新后的描述
     * @param nextStatus      更新后的状态
     * @param now             当前时间（作为新的更新时间）
     * @return 更新后的知识库聚合根（新实例）
     */
    public KnowledgeBase update(
            String nextName,
            String nextDescription,
            KnowledgeBaseStatus nextStatus,
            Instant now) {
        // 保留 kbId 和 createdAt 不变，仅刷新 updatedAt
        return new KnowledgeBase(
                kbId,               // 标识不变
                workspaceId,        // 工作区不变
                nextName,           // 新名称（已由应用层规整化）
                nextDescription,    // 新描述（已由应用层规整化）
                nextStatus,         // 新状态（已由应用层解析）
                createdAt,          // 创建时间保持不变
                now);               // 更新时间刷新为当前时刻
    }
}
