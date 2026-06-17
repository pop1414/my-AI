package io.github.spike.myai.qa.domain.model;

/**
 * 查询意图类型枚举。
 *
 * <p>定义用户查询的意图分类，用于驱动检索策略路由。
 * 由 {@code QueryClassifierPort} 返回，应用层根据类型决定走检索或跳过检索。
 *
 * @author spike
 * @since 1.0.0
 */
public enum QueryType {

    /** 事实性查询 — 询问定义、概念或具体事实，如"什么是向量数据库" */
    FACTOID,

    /** 操作/步骤查询 — 询问操作方法或步骤，如"如何配置 Flyway" */
    PROCEDURAL,

    /** 对比查询 — 比较两个或多个事物，如"Spring AI 和 LangChain 区别" */
    COMPARATIVE,

    /** 闲聊 — 问候、感谢、日常对话，如"你好"、"谢谢" */
    CHITCHAT,

    /** 通用/兜底查询 — 以上均不匹配时的默认分类 */
    GENERAL
}
