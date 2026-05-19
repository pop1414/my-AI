# ADR-0007：采用 S3 兼容文档资产存储

- 编号：ADR-0007
- 标题：采用 S3 兼容文档资产存储
- 状态：Proposed
- 日期：2026-05-19

## 背景

当前 ingest 主链路已经明确区分 source 与 artifacts：

- source 是用户上传的原始文件，用于审计、重处理和未来原版预览。
- artifacts 是文档处理链路生成的版本级产物，其中 `cleaned.md` 是正文读取与问答核对的正式中间文本产物。

本地文件系统实现已经满足早期开发和单机验证，但随着系统进入更真实的本地演示、私有化部署和后续容量扩展阶段，source 与 artifacts 需要从单机目录演进为更稳定的对象存储能力。

现有代码已经通过 `DocumentSourceStorage` 与 `DocumentProcessingArtifactStorage` 隔离业务层与存储介质，并通过 `DocumentStorageKeyResolver` 统一 key 规则。该边界适合引入对象存储适配器，而不是让应用层直接依赖具体产品 SDK。

## 备选方案

1. 继续使用本地文件系统
2. 使用 PostgreSQL `bytea` 或 large object 存储文件内容
3. 直接绑定 RustFS 作为业务层存储实现
4. 引入 S3 兼容对象存储抽象，当前部署实现使用 RustFS
5. 直接接入云厂商对象存储

## 决策

采用 `S3-compatible document asset storage` 作为文档资产存储能力。

首期使用 RustFS 作为部署实现，但业务代码、领域术语和端口命名不绑定 RustFS。

存储规则：

- bucket：`myai-documents`
- source key：`source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`
- artifact key：`artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`

首期不把完整 object key 落库，继续由 `DocumentStorageKeyResolver` 根据稳定规则计算。

通过配置切换存储介质：

- `myai.ingest.storage.type=local`
- `myai.ingest.storage.type=s3`

默认仍为 `local`。

## 决策理由

- source 与 artifacts 是典型 write-once/read-many 文档资产，适合对象存储。
- S3 兼容协议降低未来从 RustFS 切换到 MinIO、Ceph、AWS S3 或其他兼容实现的成本。
- 当前端口边界已经存在，新增 S3 adapter 不需要改变 ingest 领域语义。
- 不把 object key 首期落库，可以避免数据库事实与对象 key 规则重复维护。
- 保留 `local` 默认模式，可以降低本地开发和现有测试成本。

## 影响

### 正向影响

- 文档资产存储从单机目录演进为可部署、可备份、可扩展的对象存储能力。
- 业务层继续依赖存储端口，不直接依赖 RustFS。
- 未来替换对象存储实现时，主要影响 infrastructure adapter 与运行配置。

### 负向影响 / 风险

- 本地开发与部署需要额外维护 RustFS 服务。
- RustFS 的 S3 兼容行为需要覆盖关键路径验证，尤其是幂等写入、prefix 删除和错误映射。
- RustFS 不可用时，上传、处理和正文读取会进入失败分支；首期不做本地 fallback。
- 历史本地文件不会自动出现在 S3 模式中，需要单独迁移计划。

## 后续动作

- 编写 S3 存储配置与客户端装配。
- 实现 S3 source 存储适配器。
- 实现 S3 artifact 存储适配器。
- 补齐 S3 存储适配器测试。
- 增加 RustFS 本地开发与运维 runbook。
- 单独编写历史 `data/ingest` 文件迁移计划。
