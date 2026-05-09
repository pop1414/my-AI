package io.github.spike.myai.auth.domain.model;

/**
 * 工作区成员角色。
 *
 * <p>该枚举与数据库 {@code workspace_memberships.role} 字段保持同名映射，
 * 数据库仍使用 VARCHAR 存储，Java 侧通过枚举收口合法值。
 *
 * @author spike
 * @since 1.0.0
 */
public enum WorkspaceRole {

    /** 工作区所有者，拥有最高权限 */
    WORKSPACE_OWNER,

    /** 工作区管理员，可管理工作区内主要资源 */
    WORKSPACE_ADMIN,

    /** 工作区普通成员，需结合资源授权访问知识库和文档 */
    WORKSPACE_MEMBER
}
