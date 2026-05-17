# ADR-0006 Document Version Read Boundary

## Status

Accepted

## Context

`ingest_documents` 已从单表事实模型演进为稳定 document 身份 + latest projection。
`ingest_document_versions` 承载版本级文件事实、处理事实、错误事实和来源事实。

迁移期主表仍保留 `file_hash`、`filename`、`file_size`、`status`、`processing_metadata`
等旧版本事实字段。它们只能作为兼容镜像写入，不能再作为新生产读路径的事实入口。

## Decision

- 文档聚合读取通过 `ingest_documents.latest_version_number` 关联 `ingest_document_versions`。
- 上传幂等的文件哈希判断读取 `ingest_document_versions.file_hash`，删除排除读取 `ingest_documents.latest_status`。
- 上传幂等只排除 `DELETED`。`DELETING` 期间仍命中原 document，以保持应用层查重、兼容唯一索引 `uk_ingest_documents_kb_file_hash` 和删除失败回滚语义一致。
- 文档列表读取 `latest_filename/latest_status` 与 version 表中的 `file_size/failure_reason`。
- 文档详情状态读取同样以 latest projection + latest version fact 为主视图事实源，返回当前最新版本的状态、来源文件名、版本号和来源类型。
- 文档版本正文读取通过 `documentId + versionNumber` 定位版本级处理产物，并读取该版本对应的 `cleaned.md`。
- `cleaned.md` 是版本级 artifact，不是 document 级共享文件；artifact key 必须包含 `workspaceId`、`documentId`、`versionNumber` 和 artifact 名称。
- 源文件和处理产物在对象存储中逻辑隔离；首期可使用同一 MinIO bucket 的不同 prefix，例如 `source/...` 与 `artifacts/...`。
- 应用层通过版本处理产物存储端口读取正文，不直接依赖本地文件路径或 MinIO SDK。
- 文档版本正文读取使用统一接口 `GET /api/v1/documents/{documentId}/content`，通过必填查询参数 `source` 表达读取意图。
- `source=LATEST` 表示服务端选择当前 latest version 并忠实表达 latest 状态；该分支不能自动回退到旧版本，也不能通过前端传入 `versionNumber` 来模拟 latest。
- `source=ASKABLE_BASELINE` 表示服务端选择当前 QA 可问答基线版本，用于问答引用侧栏和普通读者正文核对。
- `source=EXPLICIT_VERSION&versionNumber={versionNumber}` 表示读取指定历史版本正文；只有具备目标 `document` 管理权限的用户可读取任意历史版本正文。
- 普通 `KB_READER` 只能读取问答基线版本正文，不能通过 `source=EXPLICIT_VERSION` 或伪造 `versionNumber` 浏览任意历史版本正文。
- `source` 是稳定业务语义，不是前端展示标签。服务端必须按 `source` 分支分别选择版本、校验权限并映射错误码。
- 正文读取不改变问答基线，也不影响后续问答版本选择。
- `INDEXED` 与 `FAILED` 版本只要存在 `cleaned.md` 即可读取正文；处理中版本正文未生成时返回 `CONTENT_NOT_READY`。
- 版本状态已完成但 `cleaned.md` 缺失时返回 `CONTENT_ARTIFACT_MISSING`，不得伪装成空正文。
- 正文读取链路不得在 artifact 缺失时同步重新解析源文件。
- 回退产生的新版本必须拥有自己的版本级 `cleaned.md`；读取新版本正文不得直接指向被回退历史版本 artifact。
- `DELETED` 文档不开放正文读取，即使是管理人员也只允许查看终态和版本历史元数据。
- 正文响应以 Markdown 为契约，字段名使用 `contentMarkdown`；首期返回完整正文但必须设置服务端最大读取大小。
- 正文读取超出服务端最大读取大小时返回 `CONTENT_TOO_LARGE`，不得静默截断正文。
- 源文件只作为审计、重处理和未来原版预览来源，不作为正文读取默认来源；当前阶段不提供源文件下载能力；`vector_store` chunk 只作为检索与分块预览事实源，不作为完整正文事实源。
- 测试验收必须覆盖默认正文、问答基线正文、指定版本正文、权限边界、artifact 缺失、正文未生成、正文过大和 `DELETED` 文档拒绝正文读取。
- E2E 纳入专项验收，但排在后端接口和前端单元/组件测试之后，重点验证普通读者不能绕过问答基线查看历史版本正文。
- 知识库已索引文档计数读取 `ingest_documents.latest_status`。
- schema 自检同时校验主表 latest projection、旧兼容镜像列、version 事实列与 version 文件哈希索引。

## Consequences

主表旧版本事实字段仍会被写入，但它们只是迁移兼容镜像。
后续代码审阅时，若发现生产读路径使用 `ingest_documents.file_hash/filename/file_size/status/processing_metadata`
推导版本语义，应视为边界回退。
若发现文档版本正文读取从源文件实时解析，或从 `vector_store` chunk 拼接完整正文，也应视为边界回退。

## Physical Drop Plan

1. 确认生产读路径与报表均不再依赖主表旧版本事实列。
2. 将上传幂等唯一约束迁移到以 version 事实为来源的约束或应用级冲突检查。
3. 删除 `uk_ingest_documents_kb_file_hash` 旧兼容索引。
4. 移除 `JdbcDocumentRepository` 中对主表旧版本事实列的兼容镜像写入。
5. 通过 Flyway 删除 `ingest_documents.file_hash`、`filename`、`file_size`、`status`、`processing_metadata`。
6. 更新 schema 自检，移除旧兼容镜像列检查。
