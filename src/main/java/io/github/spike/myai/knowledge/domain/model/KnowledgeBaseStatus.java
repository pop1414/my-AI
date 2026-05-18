package io.github.spike.myai.knowledge.domain.model;

/**
 * 知识库生命周期状态枚举。
 *
 * <p>该枚举定义知识库在其生命周期中的两种状态：
 * <ul>
 *   <li>{@link #ACTIVE} —— 启用状态，知识库可正常接收文档索引与检索请求；</li>
 *   <li>{@link #INACTIVE} —— 停用状态，知识库暂时不可用（如维护中、归档等场景）。</li>
 *   <li>{@link #DELETED} —— 已删除状态，默认从业务列表隐藏且不可参与上传、问答和授权治理。</li>
 * </ul>
 *
 * <p>设计说明：
 * <ul>
 *   <li>当前版本支持启用、停用和软删除三种状态；</li>
 *   <li>对外 API 中以字符串形式暴露状态值（通过 {@code name()} 方法），
 *       避免前端直接依赖枚举常量。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public enum KnowledgeBaseStatus {

    /** 启用：知识库处于正常工作状态 */
    ACTIVE,

    /** 停用：知识库处于不可用状态（维护/归档等） */
    INACTIVE,

    /** 删除：知识库已软删除，保留历史数据与审计追溯 */
    DELETED
}
