package io.github.spike.myai.auth.interfaces.rest.dto;

/**
 * 授予或更新文档授权请求 DTO。
 * <p>
 * 用于接收 {@code PUT /api/v1/admin/documents/{documentId}/grants/{userId}} 接口的请求体。
 * 仅包含一个字段——目标文档权限覆盖，由控制器提取后构造
 * {@link io.github.spike.myai.auth.application.command.UpsertDocumentGrantCommand} 传递给用例层。
 * <p>
 * 设计为 Record，天然支持 Jackson 反序列化。
 *
 * @param permission 目标文档权限字符串，需为 {@link io.github.spike.myai.auth.domain.model.DocumentPermission} 枚举的有效值
 * @author spike
 * @since 1.0.0
 */
public record UpsertDocumentGrantRequest(String permission) {
}
