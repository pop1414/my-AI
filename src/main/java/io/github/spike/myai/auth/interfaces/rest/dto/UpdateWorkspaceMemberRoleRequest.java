package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 调整工作区成员角色请求 DTO。
 * <p>
 * 用于接收 {@code PATCH /api/v1/admin/members/{userId}/role} 接口的请求体。
 * 仅包含一个字段——目标工作区角色，由控制器提取后构造
 * {@link io.github.spike.myai.auth.application.command.UpdateWorkspaceMemberRoleCommand} 传递给用例层。
 * <p>
 * 设计为 Record，天然支持 Jackson 反序列化，字段与 JSON 属性自动映射。
 *
 * @param workspaceRole 目标工作区角色字符串，需为 {@link io.github.spike.myai.auth.domain.model.WorkspaceRole} 枚举的有效值
 * @author spike
 * @since 1.0.0
 */
public record UpdateWorkspaceMemberRoleRequest(String workspaceRole) {
}
