# RustFS 引入实施计划

## 1. Goal

本计划用于将 `S3-compatible document asset storage` 从已确认的 PRD / ADR 转化为阶段化实施路径。

完成后，系统应具备以下能力：

- 默认仍可使用本地文件系统保存 source 与 artifacts。
- 通过配置切换到 S3 存储模式。
- S3 模式下，新上传 source 与新生成 artifacts 写入 RustFS。
- 文档版本正文读取仍读取目标版本的 `cleaned.md`，不改变现有 ingest / qa 语义。
- RustFS 不可用时进入明确失败分支，不 fallback 到本地文件系统。

## 2. Source Material

- PRD：`docs/runbooks/plans/object-storage/object-storage-prd.md`
- ADR：`docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`
- Related docs：
  - `CONTEXT.md`
  - `docs/runbooks/operations/rustfs-object-storage.md`
  - `docs/adr/ADR-0006-document-version-read-boundary.md`
  - `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentSourceStorage.java`
  - `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java`
  - `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/DocumentStorageKeyResolver.java`

## 3. Context

当前 ingest 主链路已经明确区分 source 与 artifacts：

- source 是用户上传的原始文件，用于审计、重处理和未来原版预览。
- artifacts 是处理链路生成的版本级产物，其中 `cleaned.md` 是正文读取与问答核对的正式中间文本产物。

现有代码已经具备两个关键存储端口：

- `DocumentSourceStorage`
- `DocumentProcessingArtifactStorage`

现有 `DocumentStorageKeyResolver` 已经集中管理 source 与 artifacts 的 key 规则。ADR-0007 已接受：引入的是 `S3-compatible document asset storage` 能力，RustFS 只是当前阶段的部署实现，不作为业务层术语或领域依赖。

首期范围只覆盖新上传 source 与新生成 artifacts；既有 `data/ingest` 本地历史文件不自动迁移。

## 4. In Scope

- 验证 RustFS 本地开发环境与 bucket 前提。
- 新增 `local | s3` 存储模式配置。
- 新增 S3 client 装配。
- 新增 S3 source 存储适配器。
- 新增 S3 artifact 存储适配器。
- 验证 S3 模式下上传、处理、正文读取和删除链路。
- 更新必要运行文档与后续迁移计划入口。

## 5. Out of Scope

- 不迁移既有 `data/ingest` 本地历史文件。
- 不做前端直传对象存储。
- 不开放源文件下载能力。
- 不把完整 object key 首期落库。
- 不引入跨 bucket、跨区域复制、生命周期归档或对象版本控制策略。
- 不实现对象存储浏览 UI。

## 6. Assumptions

- 使用 AWS SDK v2 作为 S3 client。
- RustFS 本地服务可通过 Docker Compose 启动。
- 首期 bucket 固定为 `myai-documents`。
- 首期应用不负责自动创建 bucket，部署或本地初始化流程负责提前创建。
- `DocumentStorageKeyResolver` 的 key 规则继续作为 source 与 artifacts 的唯一计算入口。
- 集成测试环境允许按 profile 启动或访问 RustFS；若暂时不具备，则先以 adapter 单元测试 + 手工 smoke test 验收。

## 7. Milestones

### Milestone 1: 存储接入前置验证

- Goal：确认 RustFS 本地环境、S3 兼容访问和 bucket 前提可用，避免后端实现完成后才发现部署链路不通。
- Deliverables：
  - RustFS 本地启动验证。
  - `myai-documents` bucket 初始化约定。
  - S3 访问 smoke test 记录。
- Verification：
  - RustFS endpoint 可访问。
  - 凭证可用。
  - bucket 存在。
  - 可完成 put / get / delete 基础对象操作。
- Dependencies：
  - `infra/docker-compose.yml` 中 RustFS 服务可正常启动。
  - bucket 初始化方式明确。
- Risks：
  - RustFS 镜像、端口或凭证配置与本地环境不一致。
  - bucket 未初始化导致后端 smoke test 失败。
- Documentation updates：
  - 必要时补充 `docs/runbooks/operations/rustfs-object-storage.md` 的 bucket 初始化步骤。
- Feeds into Spec：
  - S3 client 配置字段。
  - bucket 初始化策略。
  - RustFS 健康检查要求。

### Milestone 2: 存储模式配置与 S3 Client 基线

- Goal：让应用能通过配置选择 `local` 或 `s3`，默认保持 `local`，并在 `s3` 模式下创建 S3 client。
- Deliverables：
  - `IngestProperties` 存储配置扩展。
  - S3 client 配置类。
  - 本地存储与 S3 存储的条件装配规则。
  - S3 SDK 依赖引入。
- Verification：
  - 默认配置启动时仍使用本地存储。
  - 设置 `storage.type=s3` 后能初始化 S3 client。
  - S3 必填配置缺失时报错清晰。
- Dependencies：
  - Milestone 1 的 RustFS endpoint、bucket 与凭证可用。
  - S3 SDK 版本确认。
- Risks：
  - 条件装配错误导致 `local` 默认行为被破坏。
  - 配置字段过早绑定 RustFS 产品名。
- Documentation updates：
  - 同步 `application.yaml` 示例注释。
  - 同步 RustFS runbook 配置项。
- Feeds into Spec：
  - 配置字段定义。
  - 条件 Bean 装配规则。
  - 启动失败错误信息。

### Milestone 3: Source 存储适配器

- Goal：让新上传 source 在 `s3` 模式下写入 RustFS，并保持现有 `DocumentSourceStorage` 契约。
- Deliverables：
  - S3 source storage adapter。
  - source key 复用 `DocumentStorageKeyResolver`。
  - `saveVersionIfAbsent` 幂等语义实现。
  - source 读取与按 document 删除 prefix。
- Verification：
  - `save` / `saveVersion` / `saveVersionIfAbsent` / `load` / `loadVersion` / `deleteByDocumentId` 行为通过测试。
  - 已存在且内容一致时返回幂等命中。
  - 已存在但内容不一致时抛出稳定冲突。
  - 删除 document 后 source prefix 被清理。
- Dependencies：
  - Milestone 2 的 S3 client 可注入。
  - RustFS 对对象读取、写入和 prefix list/delete 行为通过基础验证。
- Risks：
  - S3 条件写入语义与本地 `CREATE_NEW` 不完全一致。
  - 并发写入同一 source key 时需要更明确的冲突处理。
- Documentation updates：
  - 如实际幂等策略与 ADR 假设不同，更新 ADR 或 runbook。
- Feeds into Spec：
  - source 写入流程。
  - 并发冲突处理。
  - S3 异常映射。

### Milestone 4: Artifact 存储适配器与正文读取闭环

- Goal：让 `cleaned.md` 和可选调试 artifacts 在 `s3` 模式下写入 RustFS，正文读取仍按版本级 artifact 读取。
- Deliverables：
  - S3 artifact storage adapter。
  - `cleaned.md` 强制写入。
  - 可选 artifacts 写入策略。
  - artifact 读取大小限制。
  - artifact prefix 删除。
- Verification：
  - 处理完成后 RustFS 中存在版本级 `cleaned.md`。
  - 正文读取接口可读取目标版本 `cleaned.md`。
  - artifact 缺失仍返回 `CONTENT_ARTIFACT_MISSING`。
  - artifact 超过读取上限仍返回 `CONTENT_TOO_LARGE`。
  - 删除 document 后 artifacts prefix 被清理。
- Dependencies：
  - Milestone 2 的 S3 client 可注入。
  - Milestone 3 对 key 规则与异常映射的实现经验。
- Risks：
  - prefix 删除需要分页处理，遗漏对象会导致已删除文档 artifact 仍可命中。
  - 文本读取大小检查如果先完整读入内存，会削弱 `maxReadBytes` 的保护价值。
- Documentation updates：
  - 必要时更新 RustFS runbook 中 artifact 缺失和删除排障说明。
- Feeds into Spec：
  - artifact 写入规则。
  - artifact 读取错误映射。
  - prefix 删除分页策略。

### Milestone 5: 端到端接入验收

- Goal：证明 `s3` 模式下上传、处理、正文读取、删除和失败语义完整可用。
- Deliverables：
  - S3 模式关键路径集成测试或手工验收脚本。
  - RustFS 不可用时的失败验证。
  - 默认 `local` 模式回归验证。
- Verification：
  - 新上传文档 source 写入 RustFS。
  - 新生成 `cleaned.md` 写入 RustFS。
  - 正文读取语义与本地模式一致。
  - 删除 document 后 source 与 artifacts 均被清理。
  - RustFS 不可用时不 fallback 到本地文件系统。
  - 默认配置不影响现有测试。
- Dependencies：
  - Milestone 3 与 Milestone 4 完成。
  - 测试环境可访问 RustFS。
- Risks：
  - 集成测试引入外部服务后变慢或不稳定。
  - 手工验收如果没有脚本化，后续回归成本偏高。
- Documentation updates：
  - 将验收步骤沉淀到 runbook 或测试说明。
- Feeds into Spec：
  - 集成测试范围。
  - 测试 profile。
  - 失败场景定义。

### Milestone 6: 发布与后续迁移准备

- Goal：把首期上线边界、回滚方式和历史迁移后续工作固定下来。
- Deliverables：
  - RustFS runbook 根据最终实现更新。
  - 历史 `data/ingest` 迁移计划占位或独立 plan。
  - ADR-0007 保持 Accepted 状态并与实现事实一致。
- Verification：
  - 文档明确首期不迁移历史文件。
  - 回滚方式明确为配置切回 `local`。
  - 后续迁移不混入首期实现验收。
- Dependencies：
  - Milestone 5 完成并确认上线边界。
- Risks：
  - 使用者误以为切换到 `s3` 后历史本地文件仍可读。
  - 回滚后新旧存储数据分布需要额外说明。
- Documentation updates：
  - 更新 `docs/runbooks/operations/rustfs-object-storage.md`。
  - 如开始历史迁移，新增独立迁移计划。
- Feeds into Spec：
  - 历史本地文件迁移应进入单独 FS / TS，不进入首期 RustFS 接入 spec。

## 8. Testing Strategy

测试应优先覆盖外部行为，而不是 S3 SDK 调用细节。

- 配置层测试：验证默认 `local`、显式 `s3`、缺失必填配置三类行为。
- Source adapter 测试：覆盖保存、读取、幂等保存、冲突、删除。
- Artifact adapter 测试：覆盖 `cleaned.md` 强制写入、可选 artifact、读取、缺失、过大、删除。
- 应用层回归测试：继续验证正文读取不从源文件实时解析、不从 `vector_store` chunk 拼接。
- 集成或 smoke test：在 RustFS 可用时验证真实 put / get / delete 和端到端上传处理。
- 回归测试：默认 `local` 模式下现有测试应保持通过。

## 9. Risks and Mitigations

- Risk：S3 条件写入或并发语义与本地文件系统不一致。
  Mitigation：在 Source adapter spec 中明确幂等策略；必要时通过先读后比对 + 数据库版本分配约束降低冲突面。

- Risk：RustFS 不可用导致上传或处理失败。
  Mitigation：首期不 fallback；通过明确错误分支、健康检查和 runbook 降低排障成本。

- Risk：prefix 删除遗漏对象。
  Mitigation：spec 中要求分页 list/delete，并用多个对象覆盖测试。

- Risk：历史本地文件在 `s3` 模式下不可读被误判为 bug。
  Mitigation：在 runbook、计划和发布说明中明确首期不迁移历史 `data/ingest`。

- Risk：对象存储配置泄露凭证。
  Mitigation：所有密钥通过环境变量注入，不写入仓库。

## 10. Handoff to Spec

以下 milestone 需要先进入 FS / TS 再拆 issue：

- Milestone 2：配置模型、条件装配、S3 client 初始化和启动失败语义。
- Milestone 3：S3 source adapter 的幂等保存、冲突处理和删除策略。
- Milestone 4：S3 artifact adapter 的读取大小限制、缺失映射和 prefix 删除策略。
- Milestone 5：集成测试或 smoke test 的运行方式与验收边界。

Milestone 1 和 Milestone 6 可先以 runbook / plan 更新推进，必要时再拆出轻量 issue。
