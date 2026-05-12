package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 当前用户能力位响应。
 *
 * <p>包含前端菜单渲染所需的五项布尔能力位，由
 * {@link io.github.spike.myai.auth.application.service.CurrentUserCapabilitiesService}
 * 解析后通过 {@code /api/v1/auth/me} 和 {@code /api/v1/auth/login} 接口返回。
 *
 * <p>前端根据这些能力位决定：
 * <ul>
 *   <li>一级菜单是否显示（文档列表、文档上传、知识库、问答、系统管理）</li>
 *   <li>登录后的默认落点（按优先级选择第一个可访问的页面）</li>
 *   <li>无权限时展示受限提示页</li>
 * </ul>
 *
 * @param canAccessDocumentList 是否可访问文档列表页
 * @param canUploadDocument     是否可上传文档
 * @param canAccessKnowledge    是否可访问知识库
 * @param canAskQuestion        是否可进行问答
 * @param canAccessAdmin        是否可访问系统管理后台
 */
public record CurrentUserCapabilitiesResponse(
        boolean canAccessDocumentList,
        boolean canUploadDocument,
        boolean canAccessKnowledge,
        boolean canAskQuestion,
        boolean canAccessAdmin) {
}
