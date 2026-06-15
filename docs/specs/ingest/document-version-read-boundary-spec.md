# 文档版本读取边界规格

> 本文件是 [ADR-0006](../adr/ADR-0006-document-version-read-boundary.md) 的详细规格文档。
> ADR 记录决策理由，本文件记录完整约束规则。

---

## 1. 数据模型读取边界

1. 文档聚合读取通过 `ingest_documents.latest_version_number` 关联 `ingest_document_versions`。
2. 上传幂等的文件哈希判断读取 `ingest_document_versions.file_hash`，删除排除读取 `ingest_documents.latest_status`。
3. 上传幂等只排除 `DELETED`。`DELETING` 期间仍命中原 document，以保持应用层查重、兼容唯一索引 `uk_ingest_documents_kb_file_hash` 和删除失败回滚语义一致。
4. 文档列表读取 `latest_filename/latest_status` 与 version 表中的 `file_size/failure_reason`。
5. 文档详情状态读取以 latest projection + latest version fact 为主视图事实源，返回当前最新版本的状态、来源文件名、版本号和来源类型。
6. 知识库已索引文档计数读取 `ingest_documents.latest_status`。
7. schema 自检同时校验主表 latest projection、旧兼容镜像列、version 事实列与 version 文件哈希索引。

## 2. 正文读取模型

1. 文档版本正文读取通过 `documentId + versionNumber` 定位版本级处理产物，并读取该版本对应的 `cleaned.md`。
2. `cleaned.md` 是版本级 artifact，不是 document 级共享文件；artifact key 必须包含 `workspaceId`、`documentId`、`versionNumber` 和 artifact 名称。
3. 源文件和处理产物在 S3 兼容对象存储中逻辑隔离；首期可使用同一 bucket 的不同 prefix，例如 `source/...` 与 `artifacts/...`。
4. 应用层通过版本处理产物存储端口读取正文，不直接依赖本地文件路径或 S3 SDK。

## 3. 正文读取 API 设计

1. 统一接口：`GET /api/v1/documents/{documentId}/content`，通过必填查询参数 `source` 表达读取意图。
2. `source=LATEST`：服务端选择当前 latest version 并忠实表达 latest 状态；不能自动回退到旧版本，也不能通过前端传入 `versionNumber` 来模拟 latest。
3. `source=ASKABLE_BASELINE`：服务端选择当前 QA 可问答基线版本，用于问答引用侧栏和普通读者正文核对。
4. `source=EXPLICIT_VERSION&versionNumber={versionNumber}`：读取指定历史版本正文；只有具备目标 `document` 管理权限的用户可读取任意历史版本正文。
5. `source` 是稳定业务语义，不是前端展示标签。服务端必须按 `source` 分支分别选择版本、校验权限并映射错误码。
6. 正文读取不改变问答基线，也不影响后续问答版本选择。
7. 正文响应以 Markdown 为契约，字段名使用 `contentMarkdown`；首期返回完整正文但必须设置服务端最大读取大小。

## 4. 权限边界

1. 普通 `KB_READER` 只能读取问答基线版本正文，不能通过 `source=EXPLICIT_VERSION` 或伪造 `versionNumber` 浏览任意历史版本正文。
2. `DELETED` 文档不开放正文读取，即使是管理人员也只允许查看终态和版本历史元数据。

## 5. 错误处理与边界条件

1. `INDEXED` 与 `FAILED` 版本只要存在 `cleaned.md` 即可读取正文；处理中版本正文未生成时返回 `CONTENT_NOT_READY`。
2. 版本状态已完成但 `cleaned.md` 缺失时返回 `CONTENT_ARTIFACT_MISSING`，不得伪装成空正文。
3. 正文读取链路不得在 artifact 缺失时同步重新解析源文件。
4. 回退产生的新版本必须拥有自己的版本级 `cleaned.md`；读取新版本正文不得直接指向被回退历史版本 artifact。
5. 正文读取超出服务端最大读取大小时返回 `CONTENT_TOO_LARGE`，不得静默截断正文。
6. 源文件只作为审计、重处理和未来原版预览来源，不作为正文读取默认来源；当前阶段不提供源文件下载能力；`vector_store` chunk 只作为检索与分块预览事实源，不是完整正文事实源。

## 6. 测试验收要求

1. 测试验收必须覆盖：默认正文、问答基线正文、指定版本正文、权限边界、artifact 缺失、正文未生成、正文过大、`DELETED` 文档拒绝正文读取。
2. E2E 纳入专项验收，但排在后端接口和前端单元/组件测试之后，重点验证普通读者不能绕过问答基线查看历史版本正文。

## 7. Latest Projection Maintenance Module

1. `ingest_documents.latest_version_number/latest_status/latest_filename/latest_version_origin_type` 是稳定业务语义，不是允许调用方随意拼接维护的偶然字段组合。
2. 应用层 caller 只表达"推进一个 document version"或"把某个 version 提升为 latest"；latest projection maintenance 应收敛为独立 module，而不是继续散落在多个 repository 分支里的双写 SQL。
3. 该 module 的 seam 优先放在数据库侧，由单一适配器维护 latest projection 与迁移期旧兼容镜像；可接受的实现形态包括统一 SQL function/procedure，或仅负责从 `ingest_document_versions` 回写 `ingest_documents` latest projection 的 trigger。
4. 当前首批实现选择统一 SQL function seam：
   - `ingest_append_document_latest_version(...)` 负责追加新 latest version
   - `ingest_update_latest_document_version_processing(...)` 负责推进 latest version 的处理状态
5. 在该 module 落地前，`ingest_documents.latest_*` 仍视为从版本事实导出的 latest projection；新增状态推进、回退、重处理、删除逻辑时，必须同步审查主表 latest projection、版本表当前 latest 行和旧兼容镜像是否仍满足同一组 invariant。
6. 列表读取、详情读取、版本历史读取等读路径继续把 latest projection 当作稳定读 seam，不因 maintenance module 的落地而改变读契约。

## 8. 边界回退判定规则

后续代码审阅时，以下情况应视为边界回退：

- 生产读路径使用 `ingest_documents.file_hash/filename/file_size/status/processing_metadata` 推导版本语义
- 文档版本正文读取从源文件实时解析，或从 `vector_store` chunk 拼接完整正文
- 新的状态推进分支继续直接复制 `UPDATE ingest_documents` + `UPDATE ingest_document_versions` 双写模式，而不是收口到统一 latest projection seam（视为 module depth 退化）
