package io.github.spike.myai.auth.domain.model;

/**
 * 文档级权限覆盖。
 *
 * <p>该枚举与数据库 {@code document_grants.permission} 字段保持同名映射，
 * 用于在知识库授权之外表达文档级允许或拒绝规则。
 *
 * @author spike
 * @since 1.0.0
 */
public enum DocumentPermission {

    /** 允许读取文档 */
    DOC_ALLOW_READ,

    /** 允许管理文档 */
    DOC_ALLOW_MANAGE,

    /** 显式拒绝访问文档，优先级最高 */
    DOC_DENY
}
