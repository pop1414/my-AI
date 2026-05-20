# 本地 Issue 草案：RustFS 存储接入

## 生成信息

- 生成日期：2026-05-20
- 来源 PRD：`docs/runbooks/plans/object-storage/object-storage-prd.md`
- 来源 ADR：`docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`
- 来源 Plan：`docs/runbooks/plans/object-storage/rustfs-implementation-plan.md`
- 来源 Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- 当前状态：本地草案，未发布 GitHub Issues
- 目标标签：后续如发布 GitHub，AFK issue 建议使用 `ready-for-agent`

## 拆分原则

- 按 `rustfs-storage-spec.md` 中的 milestone vertical slice 拆分。
- 每个 issue 都交付一条可验证路径，不按“只写配置 / 只写类 / 只写测试”做纯横向拆分。
- 不重新定义 PRD、ADR、Plan 或 Spec 中已经确认的决策。
- 不发布到 GitHub；本文档用于本地审阅实施计划。
- GitHub 发布前，应重新检查依赖关系，并将本地编号替换或映射为真实 issue 编号。

## 总览

| 本地编号 | GitHub Issue | 标题                                            | 类型                              | 标签建议          | 阻塞              |
| -------- | ------------ | ----------------------------------------------- | --------------------------------- | ----------------- | ----------------- |
| RFS-01   | 未发布       | 验证 RustFS 本地 S3 兼容访问与 bucket 前提      | AFK                               | `ready-for-agent` | 无                |
| RFS-02   | 未发布       | 实现 `local                                     | s3` 存储模式配置与 S3 Client 装配 | AFK               | `ready-for-agent` |
| RFS-03   | 未发布       | 实现 S3 source storage adapter                  | AFK                               | `ready-for-agent` | RFS-02            |
| RFS-04   | 未发布       | 实现 S3 artifact storage adapter 与正文读取闭环 | AFK                               | `ready-for-agent` | RFS-02, RFS-03    |
| RFS-05   | 未发布       | 完成 S3 模式端到端验收与 local 回归             | AFK                               | `ready-for-agent` | RFS-03, RFS-04    |
| RFS-06   | 未发布       | 同步发布文档与历史迁移边界                      | AFK                               | `ready-for-agent` | RFS-05            |

## 建议执行顺序

1. 先执行 RFS-01，确认 RustFS endpoint、凭证、bucket 和基础 S3 操作可用。
2. 再执行 RFS-02，建立 `local|s3` 配置、S3 client 和条件装配基线。
3. 完成 RFS-03，让 source 在 S3 模式下具备保存、读取、幂等和删除能力。
4. 完成 RFS-04，让 `cleaned.md` 和可选 artifacts 在 S3 模式下具备保存、读取、大小限制和删除能力。
5. 执行 RFS-05，用端到端路径验证上传、处理、正文读取、删除和不 fallback 语义。
6. 最后执行 RFS-06，收口 runbook、发布边界和后续历史迁移说明。

## Issue 草案

### RFS-01 验证 RustFS 本地 S3 兼容访问与 bucket 前提

GitHub Issue：未发布

Type：AFK

Blocked by：None - can start immediately

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.1 Milestone 1: 存储接入前置验证`
    - TS：`4.1 Milestone 1: 存储接入前置验证`
- Plan：`docs/runbooks/plans/object-storage/rustfs-implementation-plan.md`
- Runbook：`docs/runbooks/operations/rustfs-object-storage.md`

#### What to build

验证 RustFS 作为 S3 兼容对象存储的本地运行前提。该 slice 不实现后端业务代码，目标是确认后续 S3 client 和 storage adapter 有可用的 endpoint、凭证和 bucket。

完成后，开发者应能按 runbook 启动 RustFS，确认 `myai-documents` bucket 存在，并用 S3 兼容操作完成基础 put / get / delete smoke test。

#### Spec coverage

- FS：运维或开发者可以启动 RustFS、使用凭证访问 RustFS，并在应用进入 `s3` 模式前准备好 bucket。
- TS：RustFS 只通过 S3 协议访问；smoke test 使用非业务 key，避免污染真实 `source/...` 或 `artifacts/...` prefix。

#### Acceptance criteria

- [x] RustFS endpoint 可访问。
- [x] 当前配置中的 access key / secret key 可用于 S3 兼容访问。
- [x] `myai-documents` bucket 已创建或已有明确初始化步骤。
- [x] smoke test 可完成 put / get / delete 基础对象操作。
- [x] smoke test 使用非业务 key，不污染真实 document prefix。
- [x] bucket 缺失时，runbook 能指导定位为部署前提问题。
- [x] 本 issue 不实现后端 storage adapter，不修改业务读取语义。

#### Blocked by

None - can start immediately

### RFS-02 实现 `local|s3` 存储模式配置与 S3 Client 装配

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-01

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.2 Milestone 2: 存储模式配置与 S3 Client 基线`
    - TS：`4.2 Milestone 2: 存储模式配置与 S3 Client 基线`
- ADR：`docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`

#### What to build

建立应用级存储模式配置，让系统默认继续使用本地文件系统，并在显式设置 `myai.ingest.storage.type=s3` 时初始化 S3 client 和 S3 storage adapter 所需装配基础。

该 slice 的重点是配置契约和条件装配，不实现 source 或 artifact 的具体 S3 读写逻辑。

#### Spec coverage

- FS：默认配置使用 `local`；只有 `type=s3` 时才使用 RustFS；配置缺失应在启动阶段暴露。
- TS：扩展 `IngestProperties.Storage`；新增 S3 配置结构；使用 AWS SDK v2；配置字段不得命名为 `rustfs`；domain / application 不感知配置细节。

#### Acceptance criteria

- [x] 新增 `myai.ingest.storage.type`，默认值为 `local`。
- [x] `type=local` 时不要求 S3 endpoint、bucket、access key、secret key。
- [x] `type=s3` 时会创建 S3 client。
- [x] `type=s3` 且必填 S3 配置缺失时，应用启动失败，并能定位缺失配置项。
- [x] S3 配置包含 endpoint、bucket、region、access key、secret key、path-style access。
- [x] 配置字段不使用 `rustfs` 作为配置命名。
- [x] 条件装配不会同时产生 local 和 S3 两套同类型 storage bean。
- [x] 默认 `local` 模式下，现有本地存储测试保持通过。
- [x] `domain` 和 `application` 代码不依赖 AWS SDK。

#### Blocked by

- RFS-01

### RFS-03 实现 S3 source storage adapter

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-02

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.3 Milestone 3: S3 Source Storage Adapter`
    - TS：`4.3 Milestone 3: S3 Source Storage Adapter`
- Existing port：`DocumentSourceStorage`
- Key resolver：`DocumentStorageKeyResolver`

#### What to build

实现 S3 source storage adapter，使新上传 source 在 `s3` 模式下写入 RustFS，并保持 `DocumentSourceStorage` 的保存、读取、幂等保存和删除契约。

该 slice 不改变上传 API、不新增数据库字段、不迁移历史本地 source。

#### Spec coverage

- FS：新上传 source 在 `s3` 模式下进入 RustFS；历史本地 source 不自动可读；source 删除不影响 artifacts。
- TS：实现 `DocumentSourceStorage`；复用 `DocumentStorageKeyResolver.resolveSourceKey(...)`；支持 `save`、`saveVersion`、`saveVersionIfAbsent`、`load`、`loadVersion`、`deleteByDocumentId`。

#### Acceptance criteria

- [ ] S3 source adapter 实现 `DocumentSourceStorage`。
- [ ] source object key 使用 `source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`。
- [ ] `save(documentId, filename, content)` 等价写入 version 1。
- [ ] `saveVersion(...)` 通过 `saveVersionIfAbsent(...)` 保持幂等保存契约。
- [ ] 首次 `saveVersionIfAbsent(...)` 写入成功后返回 `true`。
- [ ] 同一 key 内容一致时，`saveVersionIfAbsent(...)` 返回 `false`。
- [ ] 同一 key 内容不一致时，抛出稳定冲突，保留 `version source file content conflict` 语义。
- [ ] `load` / `loadVersion` 命中时返回完整字节，未命中时返回空。
- [ ] `load` / `loadVersion` 不 fallback 到本地文件系统或旧 document 级路径。
- [ ] `deleteByDocumentId` 删除 source prefix 下全部对象，并处理分页 list。
- [ ] source 删除不影响 artifacts prefix。
- [ ] 单元测试覆盖版本隔离、幂等命中、冲突、缺失读取和 prefix 删除。

#### Blocked by

- RFS-02

### RFS-04 实现 S3 artifact storage adapter 与正文读取闭环

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-02、RFS-03

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.4 Milestone 4: S3 Artifact Storage Adapter 与正文读取闭环`
    - TS：`4.4 Milestone 4: S3 Artifact Storage Adapter 与正文读取闭环`
- Existing port：`DocumentProcessingArtifactStorage`
- ADR：`docs/adr/ADR-0006-document-version-read-boundary.md`

#### What to build

实现 S3 artifact storage adapter，使 `cleaned.md` 和可选调试 artifacts 在 `s3` 模式下写入 RustFS，并保持文档版本正文读取的既有语义。

该 slice 不改变正文读取 API、不新增用户可见错误码、不允许从 source 实时解析或从 `vector_store` chunk 拼接完整正文。

#### Spec coverage

- FS：`cleaned.md` 仍是版本级正文事实；存储介质变化不改变正文读取 API、权限规则、响应字段或错误码。
- TS：实现 `DocumentProcessingArtifactStorage`；支持 `saveVersion`、`loadVersionArtifact`、`deleteByDocumentId`；读取前进行 object size 检查；prefix 删除处理分页。

#### Acceptance criteria

- [ ] S3 artifact adapter 实现 `DocumentProcessingArtifactStorage`。
- [ ] artifact object key 使用 `artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`。
- [ ] `saveVersion(...)` 强制写入 `cleaned.md`。
- [ ] `raw.xhtml`、`cleaned.html`、`parse-result.json` 按现有 artifacts 配置决定是否写入。
- [ ] 同一版本同名 artifact 允许覆盖，以支持处理重试写入完整结果。
- [ ] `loadVersionArtifact(...)` 命中时返回 stable key、UTF-8 正文和字节长度。
- [ ] object 不存在时，`loadVersionArtifact(...)` 返回空。
- [ ] object 超过 `maxBytes` 时，抛出 `DocumentVersionArtifactTooLargeException`。
- [ ] 超过 `maxBytes` 的对象不得先完整读入内存再判断。
- [ ] `loadVersionArtifact(...)` 不读取 source prefix、不触发重新解析、不从向量分块拼接正文。
- [ ] `deleteByDocumentId(...)` 删除 artifacts prefix 下全部对象，并处理分页 list。
- [ ] artifacts 删除不影响 source prefix。
- [ ] 正文读取回归覆盖 `LATEST`、`ASKABLE_BASELINE`、`EXPLICIT_VERSION`、`CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE` 和 `DELETED` document。

#### Blocked by

- RFS-02
- RFS-03

### RFS-05 完成 S3 模式端到端验收与 local 回归

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-03、RFS-04

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.5 Milestone 5: 端到端接入验收`
    - TS：`4.5 Milestone 5: 端到端接入验收`
- Runbook：`docs/runbooks/operations/rustfs-object-storage.md`

#### What to build

验证 `s3` 模式下上传、处理、正文读取、删除和失败语义完整可用，同时确认默认 `local` 模式不受影响。

该 slice 可以使用单独 profile、集成测试或手工 smoke test。RustFS 真实服务测试不纳入默认 Maven test。

#### Spec coverage

- FS：用户按既有流程上传、处理、读取正文和删除 document；RustFS 不可用时不 fallback。
- TS：不新增 REST API；正文读取错误码保持既有契约；真实 RustFS 验收使用单独 profile 或 smoke test。

#### Acceptance criteria

- [ ] `s3` 模式下上传文档后，RustFS 中存在对应 source object。
- [ ] `s3` 模式下处理完成后，RustFS 中存在对应版本级 `cleaned.md` object。
- [ ] `s3` 模式下正文读取成功，响应字段和错误码与本地模式一致。
- [ ] artifact 缺失仍返回 `CONTENT_ARTIFACT_MISSING`，不触发 source 重新解析。
- [ ] artifact 超过读取上限仍返回 `CONTENT_TOO_LARGE`。
- [ ] 删除 document 后，source 与 artifacts object 都被清理。
- [ ] RustFS 不可用时，上传、处理、读取或删除不 fallback 到本地文件系统。
- [ ] 默认 `local` 模式回归通过。
- [ ] 验收步骤沉淀到 runbook 或测试说明中。

#### Blocked by

- RFS-03
- RFS-04

### RFS-06 同步发布文档与历史迁移边界

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-05

#### Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- Spec coverage：
    - FS：`4.6 Milestone 6: 发布与后续迁移准备`
    - TS：`4.6 Milestone 6: 发布与后续迁移准备`
- Runbook：`docs/runbooks/operations/rustfs-object-storage.md`
- PostgreSQL runbook：`docs/runbooks/operations/postgresql-pgvector.md`

#### What to build

根据最终实现同步 RustFS 运行手册、配置说明、验收步骤和历史迁移边界，确保后续使用者清楚首期 `s3` 模式只覆盖新上传 source 与新生成 artifacts。

该 slice 不实现历史迁移、不新增双读 fallback、不新增本地到 S3 的自动同步任务。

#### Spec coverage

- FS：首期发布说明明确 `s3` 模式不迁移历史 `data/ingest`；回滚方式是配置切回 `local`。
- TS：runbook 覆盖配置、健康检查、bucket 初始化、常见问题和回滚；历史迁移后续单独 plan / spec。

#### Acceptance criteria

- [ ] RustFS runbook 与最终配置字段一致。
- [ ] RustFS runbook 明确 bucket 初始化、endpoint、region、path-style access 和凭证配置。
- [ ] RustFS runbook 明确首期不迁移历史 `data/ingest`。
- [ ] RustFS runbook 明确 RustFS 不可用时不 fallback 到本地文件系统。
- [ ] RustFS runbook 明确回滚只通过配置切回 `local`，不会自动复制 RustFS 对象回本地。
- [ ] 文档明确 PostgreSQL 备份不能替代 RustFS source/artifacts 备份。
- [ ] 如需要历史迁移，仅创建后续 plan/spec 入口，不在本 issue 中实现迁移。

#### Blocked by

- RFS-05

## 审阅提示

- 先看总览表，确认 6 个 issue 的依赖关系是否符合实际实施顺序。
- 重点审阅 RFS-02：它是后续 adapter 的装配前提，不能把配置字段绑定为 RustFS 专有命名。
- 重点审阅 RFS-03 / RFS-04：它们必须继续依赖端口和 key resolver，不能让 application 层依赖 AWS SDK。
- 重点审阅 RFS-05：它是验收 issue，不应补写新的业务语义。
- 重点审阅 RFS-06：它只收口文档和历史迁移边界，不应实现历史迁移。
- 全部 issue 都应保持首期非目标：不迁移历史文件、不做前端直传、不开放源文件下载、不落库完整 object key。
