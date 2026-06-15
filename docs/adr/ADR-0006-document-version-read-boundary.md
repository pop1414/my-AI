# ADR-0006：文档版本读取边界

- 编号：ADR-0006
- 标题：文档版本读取边界（Document Version Read Boundary）
- 状态：Accepted
- 日期：2026-05-13
- 接受日期：2026-05-13

> **详细规格**：[docs/specs/ingest/document-version-read-boundary-spec.md](../specs/ingest/document-version-read-boundary-spec.md)

## 背景

`ingest_documents` 已从单表事实模型演进为稳定 document 身份 + latest projection。
`ingest_document_versions` 承载版本级文件事实、处理事实、错误事实和来源事实。

迁移期主表仍保留 `file_hash`、`filename`、`file_size`、`status`、`processing_metadata`
等旧版本事实字段。它们只能作为兼容镜像写入，不能再作为新生产读路径的事实入口。

## 决策

### 核心数据模型

- 文档聚合读取通过 `ingest_documents.latest_version_number` 关联 `ingest_document_versions`。
- `cleaned.md` 是版本级 artifact，不是 document 级共享文件；artifact key 必须包含 `workspaceId`、`documentId`、`versionNumber`。
- 源文件和处理产物在对象存储中逻辑隔离（同一 bucket 的不同 prefix）。
- 应用层通过版本处理产物存储端口读取正文，不直接依赖本地文件路径或 S3 SDK。

### 正文读取 API

- 统一接口 `GET /api/v1/documents/{documentId}/content`，通过必填查询参数 `source` 表达读取意图。
- `source` 三个取值：`LATEST`（当前最新版本）、`ASKABLE_BASELINE`（问答基线版本）、`EXPLICIT_VERSION`（指定历史版本，需管理权限）。
- `source` 是稳定业务语义，服务端必须按分支分别选择版本、校验权限并映射错误码。
- 正文响应以 Markdown 为契约（`contentMarkdown`），必须设置服务端最大读取大小，超出返回 `CONTENT_TOO_LARGE`。

### 权限边界

- 普通 `KB_READER` 只能读取问答基线版本正文，不能浏览任意历史版本。
- `DELETED` 文档不开放正文读取，即使是管理人员也只允许查看终态和版本历史元数据。

### Latest Projection Maintenance

- `ingest_documents.latest_*` 是稳定业务语义，应收敛为独立 module（数据库侧统一 SQL function seam），不继续散落在多个 repository 的双写 SQL 中。
- 列表、详情等读路径继续把 latest projection 当作稳定读 seam，不因 maintenance module 的落地而改变读契约。

## 影响

主表旧版本事实字段仍会被写入，但它们只是迁移兼容镜像。

边界回退判定规则（详见 spec 第 8 节）：
- 生产读路径使用主表旧版本事实列推导版本语义 → 边界回退
- 正文读取从源文件实时解析或从 chunk 拼接 → 边界回退
- 新状态推进分支继续散落双写而非收口到 latest projection seam → module depth 退化

## 后续动作

1. 把 latest projection maintenance 收口为单一数据库 seam。
2. 确认生产读路径与报表均不再依赖主表旧版本事实列。
3. 将上传幂等唯一约束迁移到以 version 事实为来源的约束或应用级冲突检查。
4. 删除 `uk_ingest_documents_kb_file_hash` 旧兼容索引。
5. 移除 `JdbcDocumentRepository` 中对主表旧版本事实列的兼容镜像写入。
6. 通过 Flyway 删除 `ingest_documents.file_hash`、`filename`、`file_size`、`status`、`processing_metadata`。
7. 更新 schema 自检，移除旧兼容镜像列检查。
