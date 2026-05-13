package io.github.spike.myai.ingest.interfaces.rest.dto;

import java.util.List;

/**
 * 文档版本历史 REST 响应 DTO。
 *
 * <p>聚合了一个文档资产下所有版本历史的完整 REST 响应，
 * 由 Jackson 序列化为如下 JSON 结构：
 * <pre>{@code
 * {
 *   "documentId": "...",
 *   "sort": "versionNumber,DESC",
 *   "versions": [ ... ]
 * }
 * }</pre>
 *
 * @param documentId 文档资产 ID
 * @param sort       排序契约声明（如 "versionNumber,DESC"），供前端理解数据排列方式
 * @param versions   版本历史列表（可为空集合）
 */
public record DocumentVersionHistoryResponse(
        String documentId,
        String sort,
        List<DocumentVersionHistoryItemResponse> versions) {
}
