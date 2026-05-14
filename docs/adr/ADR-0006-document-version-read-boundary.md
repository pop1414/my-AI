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
- 知识库已索引文档计数读取 `ingest_documents.latest_status`。
- schema 自检同时校验主表 latest projection、旧兼容镜像列、version 事实列与 version 文件哈希索引。

## Consequences

主表旧版本事实字段仍会被写入，但它们只是迁移兼容镜像。
后续代码审阅时，若发现生产读路径使用 `ingest_documents.file_hash/filename/file_size/status/processing_metadata`
推导版本语义，应视为边界回退。

## Physical Drop Plan

1. 确认生产读路径与报表均不再依赖主表旧版本事实列。
2. 将上传幂等唯一约束迁移到以 version 事实为来源的约束或应用级冲突检查。
3. 删除 `uk_ingest_documents_kb_file_hash` 旧兼容索引。
4. 移除 `JdbcDocumentRepository` 中对主表旧版本事实列的兼容镜像写入。
5. 通过 Flyway 删除 `ingest_documents.file_hash`、`filename`、`file_size`、`status`、`processing_metadata`。
6. 更新 schema 自检，移除旧兼容镜像列检查。
