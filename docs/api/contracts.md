# API 契约文档

> 完整 OpenAPI spec 待补充。本文档基于源码深度扫描生成，覆盖所有已实现端点。

## 通用约定

| 约定 | 说明 |
|---|---|
| 基础路径 | `/api/v1/` |
| 认证 | 基于 Session（Cookie），除 `POST /auth/login` 外所有端点要求认证 |
| CSRF | POST/PUT/DELETE/PATCH 需携带 `X-MYAI-CSRF: 1` Header |
| 错误响应 | `{"code": "ERROR_CODE", "message": "描述"}` |
| 401 | `{"code": "UNAUTHORIZED", "message": "authentication is required"}` |
| 403 | `{"code": "FORBIDDEN", "message": "access is denied"}` |
| 分页 | `limit`（默认20）+ `offset`（默认0） |

---

## Auth — 认证与授权

### `POST /api/v1/auth/login`

用户登录。

- **认证**: 不需要
- **CSRF**: 不需要
- **请求体**: `{"username": "string", "password": "string"}`
- **响应 200**: `{"userId", "username", "displayName", "workspaceId", "workspaceRole", "capabilities"}`

### `POST /api/v1/auth/logout`

用户登出，销毁 Session。

- **响应**: `204 No Content`

### `GET /api/v1/auth/me`

获取当前已认证用户信息。

- **响应 200**: `{"userId", "username", "displayName", "workspaceId", "workspaceRole", "capabilities"}`
  - `capabilities`: `{canAccessDocumentList, canUploadDocument, canAccessKnowledge, canAskQuestion, canAccessAdmin}`

---

## Auth — 托管账号管理

### `GET /api/v1/admin/accounts`

查询当前工作区所有托管账号。

- **响应 200**: `List<ManagedAccountResponse>`

### `POST /api/v1/admin/accounts`

创建托管账号。

- **请求体**: `{"username", "displayName", "password", "workspaceRole"}`
- **响应 200**: `ManagedAccountResponse`

### `POST /api/v1/admin/accounts/member-provisions`

创建成员并初始化知识库授权（开户即授权）。

- **请求体**: `{"username", "displayName", "password", "initialKnowledgeBaseGrants": [{"kbId", "role"}]}`
- **响应 200**: `ManagedAccountResponse`

### `PATCH /api/v1/admin/accounts/{userId}/status`

更新账号状态。

- **请求体**: `{"userStatus": "ACTIVE|DISABLED"}`
- **响应 200**: `ManagedAccountResponse`

### `POST /api/v1/admin/accounts/{userId}/password/reset`

重置账号密码。

- **请求体**: `{"password": "string"}`
- **响应 200**: `ManagedAccountResponse`

### `DELETE /api/v1/admin/accounts/{userId}/membership`

移除成员关系（软删除）。

- **响应**: `204 No Content`

---

## Auth — 工作区成员管理

### `GET /api/v1/admin/members`

查询工作区所有活跃成员。

- **响应 200**: `List<WorkspaceMemberResponse>`

### `PATCH /api/v1/admin/members/{userId}/role`

更新成员工作区角色。

- **请求体**: `{"workspaceRole": "WORKSPACE_OWNER|WORKSPACE_ADMIN|WORKSPACE_MEMBER"}`
- **响应 200**: `WorkspaceMemberResponse`

### `GET /api/v1/admin/members/{userId}/knowledge-base-grants`

查询指定成员的知识库授权列表。

- **响应 200**: `List<KnowledgeBaseGrantResponse>`

### `PUT /api/v1/admin/members/{userId}/knowledge-base-grants:batch`

批量替换成员知识库授权（声明式同步）。

- **请求体**: `{"assignments": [{"kbId", "role"}]}`
- **响应 200**: `List<KnowledgeBaseGrantResponse>`

### `GET /api/v1/admin/members/{userId}/document-grants`

查询指定成员的文档授权列表。

- **响应 200**: `List<DocumentGrantResponse>`

### `PUT /api/v1/admin/members/{userId}/document-grants:batch`

批量替换成员文档授权（声明式同步）。

- **请求体**: `{"assignments": [{"documentId", "permission"}]}`
- **响应 200**: `List<DocumentGrantResponse>`

---

## Auth — 授权管理

### `GET /api/v1/admin/documents/{documentId}/grants`

查询指定文档下所有活跃授权记录。

### `PUT /api/v1/admin/documents/{documentId}/grants/{userId}`

授予或更新文档授权。

- **请求体**: `{"permission": "DOC_ALLOW_READ|DOC_ALLOW_MANAGE|DOC_DENY"}`

### `DELETE /api/v1/admin/documents/{documentId}/grants/{userId}`

回收文档授权。

### `GET /api/v1/admin/knowledge-bases/{kbId}/grants`

查询指定知识库下所有活跃授权记录。

### `PUT /api/v1/admin/knowledge-bases/{kbId}/grants/{userId}`

授予或更新知识库授权。

- **请求体**: `{"role": "KB_MANAGER|KB_CONTRIBUTOR|KB_READER|KB_ASKER"}`

### `DELETE /api/v1/admin/knowledge-bases/{kbId}/grants/{userId}`

回收知识库授权。

---

## Auth — 审计事件

### `GET /api/v1/admin/audit-events`

分页查询审计事件。

- **查询参数**: `eventType`, `actorUserId`, `actorKeyword`, `targetType`, `targetId`, `outcome`, `occurredFrom`, `occurredTo`, `limit`(20), `offset`(0)
- **响应 200**: `{"items": [...], "total", "limit", "offset"}`

---

## Ingest — 文档管理

### `GET /api/v1/documents`

查询文档分页列表。

- **查询参数**: `kbId`(可选), `status`(可选), `filename`(可选), `limit`(20), `offset`(0)
- **响应 200**: `{"items": [...], "total", "limit", "offset"}`

### `POST /api/v1/documents/upload`

上传并受理文档入库。

- **请求**: `multipart/form-data`，字段 `file`(必填), `kbId`(可选，默认 "default")
- **响应 200**: `{"documentId", "status": "ACCEPTED"}`
- **幂等**: 同 `kbId + fileHash(SHA-256)` 复用已有 documentId

### `GET /api/v1/documents/{documentId}/status`

查询文档处理状态。

- **响应 200**: `{"documentId", "kbId", "latestVersionNumber", "filename", "latestVersionOriginType", "status", "processingMetadata"}`
- **状态值**: `UPLOADED`, `INGESTING`, `INDEXED`, `FAILED`, `DELETED`

### `GET /api/v1/documents/{documentId}/versions`

查询文档版本历史。

### `GET /api/v1/documents/{documentId}/content`

查询文档正文（cleaned.md）。

- **查询参数**: `source`(`LATEST` | `ASKABLE_BASELINE` | `EXPLICIT_VERSION`), `versionNumber`(source=EXPLICIT_VERSION 时必填)

### `POST /api/v1/documents/{documentId}/versions`

上传新版本。

- **请求**: `multipart/form-data`，字段 `file`(必填), `expectedLatestVersionNumber`(必填, 乐观锁)
- **响应 200**: `DocumentVersionUploadResponse`

### `POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback`

版本回退。

- **查询参数**: `expectedLatestVersionNumber`(必填)

### `GET /api/v1/documents/{documentId}/chunks/preview`

分块预览（调试用途）。

- **查询参数**: `limit`(20, 最大200), `offset`(0), `previewChars`(200, 20~2000)

### `POST /api/v1/documents/{documentId}/reprocess`

触发文档重处理。

- **查询参数**: `expectedLatestVersionNumber`(可选)

### `DELETE /api/v1/documents/{documentId}`

删除文档资产。

- **查询参数**: `expectedLatestVersionNumber`(可选)
- **响应**: `204 No Content`

---

## Knowledge — 知识库管理

### `GET /api/v1/knowledge-bases`

查询知识库列表。

- **查询参数**: `includeDeleted`(默认 false)
- **响应 200**: `List<KnowledgeBaseResponse>`，含 `indexedDocumentCount`

### `POST /api/v1/knowledge-bases`

创建知识库。

- **请求体**: `{"name"(必填), "description"(选填), "status"(选填，默认 ACTIVE)}`
- **响应 201**: `KnowledgeBaseResponse`

### `PATCH /api/v1/knowledge-bases/{kbId}`

更新知识库信息。

- **请求体**: `{"name"(选填), "description"(选填), "status"(选填)}` — null 表示不修改

### `DELETE /api/v1/knowledge-bases/{kbId}`

软删除知识库。

- **响应**: `204 No Content`

---

## QA — 文档问答

### `POST /api/v1/qa/ask`

基于知识库的 RAG 问答（同步返回）。

- **请求体**: `{"question"(必填), "kbId"(选填，默认 "default"), "topK"(选填，默认 5，范围 1~20)}`
- **响应 200**:
```json
{
  "answer": "LLM 生成的回答",
  "references": [
    {
      "documentId": "doc-xxx",
      "chunkIndex": 0,
      "contentPreview": "引用内容预览（最多200字符）...",
      "sourceVersionNumber": 1,
      "sourceUpdatedAt": "2026-01-01T00:00:00Z",
      "isLatestVersion": true,
      "latestVersionNumber": 1,
      "sourceFilename": "example.pdf"
    }
  ],
  "staleReferences": {
    "hasStaleReferences": false,
    "staleReferenceCount": 0,
    "staleDocumentCount": 0,
    "documents": []
  }
}
```

- **错误码**: 知识库不存在 → 400，知识库已停用 → 409

---

## 调试端点

### `GET /ai/embedding`

文本向量化调试接口（非 /api/v1 前缀）。

- **查询参数**: `message`(默认 "Tell me a joke")

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描（源码级提取）_
