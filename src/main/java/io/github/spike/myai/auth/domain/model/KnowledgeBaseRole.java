package io.github.spike.myai.auth.domain.model;

/**
 * 知识库授权角色。
 *
 * <p>该枚举与数据库 {@code knowledge_base_grants.role} 字段保持同名映射，
 * 后续授权服务基于该类型表达知识库级权限规则。
 *
 * @author spike
 * @since 1.0.0
 */
public enum KnowledgeBaseRole {

    /** 知识库管理者，可管理知识库配置与授权 */
    KB_MANAGER,

    /** 知识库贡献者，可上传、重处理和维护内容 */
    KB_CONTRIBUTOR,

    /** 知识库读取者，可读取知识库文档内容 */
    KB_READER,

    /** 知识库问答使用者，可在问答场景使用知识库内容 */
    KB_ASKER
}
