# S3 兼容文档资产存储 PRD

## 背景

当前 ingest 主链路已经形成稳定的文档资产语义：上传源文件进入处理链路，解析与清洗后生成版本级 `cleaned.md`，问答与正文核对围绕该版本级产物展开。

现阶段源文件与处理产物主要依赖本地文件系统保存。该方式适合早期开发与单机验证，但在部署一致性、备份恢复、容量扩展和未来私有化部署方面存在限制。

本阶段引入 `S3-compatible document asset storage`，使用 RustFS 作为当前部署实现，用于保存新上传 source 与新生成 artifacts。

## 目标

- 让新上传的原文档 source 可写入 S3 兼容对象存储。
- 让新生成的 `cleaned.md` 与可选调试 artifacts 可写入 S3 兼容对象存储。
- 保持 `DocumentSourceStorage` 与 `DocumentProcessingArtifactStorage` 作为业务访问边界。
- 保持文档版本正文读取语义不变：读取目标版本的 `cleaned.md`，不从源文件实时解析，不从 `vector_store` chunk 拼接。
- 通过配置在 `local` 与 `s3` 存储模式之间切换，默认仍为 `local`。
- 提供 RustFS 本地开发与部署运行说明。

## 非目标

- 不在首期迁移既有 `data/ingest` 本地历史文件。
- 不做前端直传对象存储。
- 不开放源文件下载能力。
- 不把完整 object key 首期落库。
- 不引入跨 bucket、跨区域复制、生命周期归档或对象版本控制策略。
- 不把 RustFS 作为业务层类型或领域术语。

## 术语

- `S3-compatible document asset storage`：文档资产对象存储能力，面向 source 与 artifacts。
- RustFS：当前阶段采用的 S3 兼容对象存储部署实现。
- source：用户上传的原始文件。
- artifact：处理链路生成的版本级产物，例如 `cleaned.md`、`raw.xhtml`、`cleaned.html`、`parse-result.json`。

## 范围

### In Scope

- 新增 S3 存储配置。
- 新增 S3 client 装配。
- 新增 S3 source 存储适配器。
- 新增 S3 artifact 存储适配器。
- 增加 RustFS Docker Compose 或本地部署说明。
- 增加必要的单元测试和集成验证说明。

### Out of Scope

- 历史本地文件迁移。
- 对象存储浏览 UI。
- 源文件下载或预览。
- 存储配额、生命周期规则和多副本治理。

## 存储规则

首期采用单 bucket：

- bucket：`myai-documents`
- source key：`source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`
- artifact key：`artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`

object key 继续由 `DocumentStorageKeyResolver` 计算，不在数据库中重复持久化完整 key。

## 行为要求

- `storage.type=local` 时保持现有本地文件系统行为。
- `storage.type=s3` 时，新上传 source 与新生成 artifacts 写入 RustFS。
- RustFS 不可用时不回退写入本地文件系统。
- source 幂等保存应保持现有语义：已存在且内容一致视为幂等命中，已存在但内容不一致视为冲突。
- artifacts 写入允许覆盖同一版本同名产物，以支持处理重试写入完整结果。
- 删除文档时应清理对应 source 与 artifacts prefix。

## 验收标准

- 可以通过配置切换 `local` 与 `s3` 存储模式。
- S3 模式下上传新文档后，RustFS 中存在对应 source object。
- S3 模式下处理完成后，RustFS 中存在对应版本级 `cleaned.md`。
- 正文读取接口在 S3 模式下可读取目标版本 `cleaned.md`。
- artifact 缺失仍返回 `CONTENT_ARTIFACT_MISSING`，不触发源文件实时解析。
- artifact 超过读取上限仍返回 `CONTENT_TOO_LARGE`。
- RustFS 不可用时，上传、处理或正文读取进入明确失败分支，不静默 fallback。
- 默认配置不影响现有本地开发和测试。

## 风险

- RustFS 的 S3 兼容行为需要通过本项目关键路径验证，尤其是条件写入、prefix 删除和错误码映射。
- 引入对象存储后，本地开发需要额外服务依赖。
- 如果历史文件迁移过早并入首期，会扩大回滚和验收复杂度。
