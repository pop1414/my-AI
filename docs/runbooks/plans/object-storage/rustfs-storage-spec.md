# RustFS 存储接入 FS / TS

## 1. Source Material

- PRD：`docs/runbooks/plans/object-storage/object-storage-prd.md`
- ADR：
  - `docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`
  - `docs/adr/ADR-0006-document-version-read-boundary.md`
- Plan：`docs/runbooks/plans/object-storage/rustfs-implementation-plan.md`
- Related docs：
  - `CONTEXT.md`
  - `docs/runbooks/operations/rustfs-object-storage.md`
  - `docs/runbooks/operations/postgresql-pgvector.md`
  - `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentSourceStorage.java`
  - `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java`
  - `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/DocumentStorageKeyResolver.java`
  - `src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java`

## 2. Global Scope

### In Scope

- 新增 `local | s3` 存储模式配置。
- 新增 S3 client 装配，用于连接 RustFS。
- 新增 S3 source 存储适配器。
- 新增 S3 artifact 存储适配器。
- 保持 `DocumentSourceStorage` 与 `DocumentProcessingArtifactStorage` 作为业务访问边界。
- 保持 `DocumentStorageKeyResolver` 作为 source 与 artifacts object key 的唯一规则入口。
- 验证 S3 模式下上传、处理、正文读取和删除链路。
- 明确 RustFS 不可用时不 fallback 到本地文件系统。

### Out of Scope

- 不迁移既有 `data/ingest` 本地历史文件。
- 不做前端直传对象存储。
- 不开放源文件下载或源文件预览能力。
- 不把完整 object key 首期落库。
- 不引入对象版本控制、生命周期归档、跨 bucket 或跨区域复制。
- 不实现对象存储浏览 UI。
- 不改变文档版本正文读取 API 契约。

## 3. Global Invariants

- `S3-compatible document asset storage` 是能力名称，RustFS 是当前部署实现。
- 业务层不得依赖 RustFS 产品名、RustFS SDK 或 RustFS 专有能力。
- domain / application 不得依赖 AWS SDK；只有 infrastructure adapter 可以依赖 AWS SDK。
- 默认存储模式必须为 `local`。
- `s3` 模式只覆盖切换后新上传 source 与新生成 artifacts。
- 历史 `data/ingest` 本地文件不会在 `s3` 模式下自动可读。
- bucket 首期固定为 `myai-documents`，但应通过配置注入。
- 应用首期不负责自动创建 bucket；bucket 缺失应作为部署问题暴露。
- source 与 artifacts 必须使用不同 prefix。
- `cleaned.md` 必须按 `workspaceId + documentId + versionNumber` 唯一定位。
- 正文读取不得在 artifact 缺失时同步重新解析 source。
- 正文读取不得从 `vector_store` chunk 拼接完整正文。
- RustFS 不可用时不得 fallback 写入本地文件系统。
- PostgreSQL 不存储对象内容；RustFS 备份与 PostgreSQL 备份互不替代。
- 本功能不新增用户角色、权限、document 状态或对外 REST API。

## 4. Milestone Specs

### 4.1 Milestone 1: 存储接入前置验证

#### Functional Specification

- 运维或开发者可以启动 RustFS 本地服务。
- 运维或开发者可以使用配置中的 access key / secret key 访问 RustFS。
- `myai-documents` bucket 必须在应用进入 `s3` 模式前存在。
- bucket 初始化属于部署或本地初始化职责，不属于应用运行时职责。

#### Technical Specification

- RustFS 只通过 S3 协议访问。
- smoke test 至少覆盖 put / get / delete 基础对象操作。
- smoke test 使用的对象 key 不应复用真实 document key，避免污染业务 prefix。
- 运行手册需要说明 endpoint、bucket、region、path-style access 和凭证配置。

#### Verification

- RustFS endpoint 可访问。
- 凭证可用。
- bucket 存在。
- 可完成 put / get / delete 基础对象操作。
- bucket 缺失时，验证能定位为部署前提问题。

#### Open Questions

- 是否需要提供自动 bucket 初始化脚本。
  - Current decision：首期不要求；如后续需要，放入 runbook 或单独 issue。

### 4.2 Milestone 2: 存储模式配置与 S3 Client 基线

#### Functional Specification

- 默认配置下，系统继续使用本地文件系统保存 source 与 artifacts。
- 只有当 `myai.ingest.storage.type=s3` 时，系统才使用 RustFS。
- 存储介质切换属于部署配置，不对普通用户暴露为前端功能。
- `s3` 模式配置缺失时，应用应启动失败，而不是运行到首次上传或处理时才失败。

#### Technical Specification

推荐配置结构：

```yaml
myai:
  ingest:
    storage:
      type: ${INGEST_STORAGE_TYPE:local}
      root-dir: ${INGEST_STORAGE_ROOT_DIR:data/ingest}
      s3:
        endpoint: ${INGEST_STORAGE_S3_ENDPOINT:}
        bucket: ${INGEST_STORAGE_S3_BUCKET:myai-documents}
        region: ${INGEST_STORAGE_S3_REGION:us-east-1}
        access-key: ${INGEST_STORAGE_S3_ACCESS_KEY:}
        secret-key: ${INGEST_STORAGE_S3_SECRET_KEY:}
        path-style-access: ${INGEST_STORAGE_S3_PATH_STYLE_ACCESS:true}
```

配置规则：

- `type` 默认 `local`。
- `type=local` 时，S3 配置可以为空。
- `type=s3` 时，`endpoint`、`bucket`、`access-key`、`secret-key` 必须有效。
- `path-style-access` 默认 `true`，适配 RustFS 本地部署。
- 配置字段不得命名为 `rustfs`。
- 使用 AWS SDK v2 作为 S3 client。

模块职责：

- `infrastructure.config` 扩展 `IngestProperties.Storage`。
- `infrastructure.config` 装配 S3 client。
- `infrastructure.config` 根据 storage type 条件装配 local 或 S3 storage adapter。
- `domain` 和 `application` 不感知配置细节。

#### Verification

- 默认 `local` 模式不要求 S3 配置。
- `type=s3` 时创建 S3 client。
- `type=s3` 且必填配置缺失时启动失败。
- 条件装配不会同时产生 local 和 S3 两套同类型 storage bean。
- 默认配置下现有本地存储测试保持通过。

#### Open Questions

- 是否将配置缺失错误做成更细粒度的配置校验异常。
  - Current decision：需要可定位配置项，但不要求新增业务错误码。

### 4.3 Milestone 3: S3 Source Storage Adapter

#### Functional Specification

- 新上传 source 在 `s3` 模式下写入 RustFS。
- source 写入、读取、幂等保存和删除行为保持 `DocumentSourceStorage` 端口契约。
- 历史本地 source 不在 `s3` 模式下自动可读。
- source 删除只影响 source prefix，不影响 artifacts prefix。

#### Technical Specification

S3 source adapter 必须实现 `DocumentSourceStorage`。

object key 由 `DocumentStorageKeyResolver.resolveSourceKey(...)` 生成：

```text
source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}
```

`save` / `saveVersion`：

- `save(documentId, filename, content)` 等价写入 version 1。
- `saveVersion(documentId, versionNumber, filename, content)` 调用 `saveVersionIfAbsent`。

`saveVersionIfAbsent`：

1. 根据 key 判断对象是否存在。
2. 不存在时写入对象，并返回 `true`。
3. 存在时读取现有对象内容。
4. 内容一致时返回 `false`。
5. 内容不一致时抛出稳定冲突异常。

首期不强依赖 RustFS 条件写入能力。并发同 key 写入主要依赖应用侧版本分配与内容比对保证一致性。

`load` / `loadVersion`：

- 命中时返回完整字节数组。
- 未命中时返回 `Optional.empty()`。
- 不回退到本地文件系统。
- 不回退到旧 document 级路径。

`deleteByDocumentId`：

- 删除 `source/{workspaceId}/documents/{documentId}/` prefix 下全部对象。
- 必须处理分页 list。
- 只清 source prefix，不触碰 artifacts prefix。

错误处理：

- S3 写入失败：抛出基础设施异常，由上传或版本上传链路映射为请求失败。
- source 内容冲突：保留稳定冲突消息 `version source file content conflict`。

#### Verification

- version 1 source 写入与读取。
- 不同 version 使用相同 filename 时内容隔离。
- `saveVersionIfAbsent` 首次写入返回 `true`。
- 相同内容重复写入返回 `false`。
- 不同内容重复写入抛冲突。
- 缺失 source 返回空。
- 删除 source prefix 后对象不可读取。
- 删除 source prefix 不影响 artifacts prefix。

#### Open Questions

- 是否需要后续引入条件写入加强并发保护。
  - Current decision：首期不依赖，后续根据 RustFS 兼容验证决定。

### 4.4 Milestone 4: S3 Artifact Storage Adapter 与正文读取闭环

#### Functional Specification

- `cleaned.md` 在 `s3` 模式下写入 RustFS。
- `cleaned.md` 仍是版本级正文事实，不改变正文读取 API、响应字段、权限规则或错误码。
- `raw.xhtml`、`cleaned.html`、`parse-result.json` 继续按配置决定是否保留。
- artifact 删除只影响 artifacts prefix，不影响 source prefix。

#### Technical Specification

S3 artifact adapter 必须实现 `DocumentProcessingArtifactStorage`。

object key 由 `DocumentStorageKeyResolver.resolveArtifactKey(...)` 生成：

```text
artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}
```

`saveVersion` 必须写入：

- `cleaned.md`

`saveVersion` 按配置写入：

- `raw.xhtml`
- `cleaned.html`
- `parse-result.json`

写入规则：

- `cleaned.md` 不受调试产物配置影响，必须写入。
- 同一版本同名 artifact 允许覆盖，用于处理重试写入完整结果。

`loadVersionArtifact`：

1. 根据 key 查询对象。
2. 对象不存在时返回 `Optional.empty()`。
3. 对象存在时先判断对象大小。
4. 大小超过 `maxBytes` 时抛出 `DocumentVersionArtifactTooLargeException`。
5. 大小符合限制时按 UTF-8 读取文本。
6. 返回 `DocumentVersionArtifactContent(key, content, contentLength)`。

读取要求：

- 不读取 source prefix。
- 不触发重新解析。
- 不从向量分块拼接正文。
- 不在超过 `maxBytes` 后才完整读取内容。

`deleteByDocumentId`：

- 删除 `artifacts/{workspaceId}/documents/{documentId}/` prefix 下全部对象。
- 必须处理分页 list。
- 只清 artifacts prefix，不触碰 source prefix。

错误处理：

- 处理阶段写入 artifact 失败时，进入既有处理失败链路。
- object 不存在：adapter 返回空，由应用层按版本状态映射为 `CONTENT_NOT_READY` 或 `CONTENT_ARTIFACT_MISSING`。
- object 过大：adapter 抛 `DocumentVersionArtifactTooLargeException`，应用层映射为 `CONTENT_TOO_LARGE`。
- S3 读取异常：作为基础设施异常处理，不伪装成 artifact missing。

#### Verification

- `cleaned.md` 强制写入。
- `raw.xhtml` / `cleaned.html` / `parse-result.json` 按配置写入。
- `loadVersionArtifact` 返回 stable key、正文和字节长度。
- artifact 缺失返回空。
- artifact 超过 `maxBytes` 抛 `DocumentVersionArtifactTooLargeException`。
- 删除 artifacts prefix 不影响 source prefix。
- prefix 删除覆盖多对象场景。
- 正文读取继续覆盖 `LATEST`、`ASKABLE_BASELINE`、`EXPLICIT_VERSION`、权限边界和 `DELETED` document。

#### Open Questions

- 是否需要为 S3 读取异常新增业务错误码。
  - Current decision：首期不新增；基础设施异常不伪装成 artifact missing。

### 4.5 Milestone 5: 端到端接入验收

#### Functional Specification

- `s3` 模式下，用户按既有方式上传文档，source 写入 RustFS。
- worker 按既有方式处理文档，artifacts 写入 RustFS。
- 用户按既有方式读取正文，系统读取目标版本 `cleaned.md`。
- 用户按既有方式删除 document，source 与 artifacts 都被清理。
- RustFS 不可用时，系统不 fallback 到本地文件系统。

#### Technical Specification

不新增对外 REST API。

以下接口契约保持不变：

- 文档上传接口。
- 文档版本上传接口。
- 文档处理状态接口。
- 文档正文读取接口：`GET /api/v1/documents/{documentId}/content`。
- 文档删除接口。

正文读取错误码保持：

- `DOCUMENT_NOT_FOUND`
- `DOCUMENT_CONTENT_FORBIDDEN`
- `CONTENT_NOT_READY`
- `CONTENT_ARTIFACT_MISSING`
- `CONTENT_TOO_LARGE`
- `VERSION_NOT_FOUND`
- `VERSION_CONTENT_FORBIDDEN`

RustFS 真实服务测试不纳入默认 Maven test。推荐使用单独 profile 或手工 smoke test。

#### Verification

- 上传文档后 source object 存在。
- 处理完成后 `cleaned.md` object 存在。
- 正文读取成功。
- artifact 缺失仍返回 `CONTENT_ARTIFACT_MISSING`。
- artifact 超过读取上限仍返回 `CONTENT_TOO_LARGE`。
- 删除 document 后 source 与 artifacts object 被清理。
- RustFS 不可用时不 fallback。
- 默认 `local` 模式回归通过。

#### Open Questions

- 是否将 RustFS 集成测试纳入 CI。
  - Current decision：首期不纳入默认 Maven test；CI 集成另行决策。

### 4.6 Milestone 6: 发布与后续迁移准备

#### Functional Specification

- 首期发布说明必须明确：`s3` 模式只覆盖新上传 source 与新生成 artifacts。
- 切换到 `s3` 不会自动迁移历史 `data/ingest`。
- 回滚方式是配置切回 `local`，但已写入 RustFS 的对象不会自动复制回本地。

#### Technical Specification

- 不新增历史迁移逻辑。
- 不新增双读 fallback。
- 不新增本地到 S3 的自动同步任务。
- 如后续需要迁移历史 `data/ingest`，应单独编写 plan / spec。
- runbook 需要覆盖配置、健康检查、bucket 初始化、常见问题和回滚说明。

#### Verification

- runbook 与实际配置字段一致。
- 文档明确首期不迁移历史文件。
- 文档明确 PostgreSQL 备份不能替代 RustFS 备份。
- 文档明确 RustFS 不可用时不 fallback。

#### Open Questions

- 是否现在创建历史迁移 plan。
  - Current decision：本 spec 不展开；后续如切换已有数据环境再单独处理。

## 5. Cross-Cutting Testing Strategy

测试重点覆盖外部行为和端口契约，不测试 AWS SDK 内部实现。

### Configuration Tests

- 默认 `local` 模式不要求 S3 配置。
- `type=s3` 时创建 S3 client。
- `type=s3` 且必填配置缺失时启动失败。
- 条件装配不会同时产生 local 和 S3 两套同类型 storage bean。

### Source Adapter Tests

- version 1 source 写入与读取。
- 不同 version 使用相同 filename 时内容隔离。
- `saveVersionIfAbsent` 首次写入返回 `true`。
- 相同内容重复写入返回 `false`。
- 不同内容重复写入抛冲突。
- 缺失 source 返回空。
- 删除 source prefix 后对象不可读取。
- 删除 source prefix 不影响 artifacts prefix。

### Artifact Adapter Tests

- `cleaned.md` 强制写入。
- `raw.xhtml` / `cleaned.html` / `parse-result.json` 按配置写入。
- `loadVersionArtifact` 返回 stable key、正文和字节长度。
- artifact 缺失返回空。
- artifact 超过 `maxBytes` 抛 `DocumentVersionArtifactTooLargeException`。
- 删除 artifacts prefix 不影响 source prefix。
- prefix 删除覆盖多对象场景。

### Application Regression Tests

- `LATEST` 正文读取。
- `ASKABLE_BASELINE` 正文读取。
- `EXPLICIT_VERSION` 权限边界。
- `CONTENT_NOT_READY`。
- `CONTENT_ARTIFACT_MISSING`。
- `CONTENT_TOO_LARGE`。
- `DELETED` document 拒绝正文读取。

### Integration / Smoke Tests

- 使用单独 profile 或手工 smoke test。
- 验证 put / get / delete。
- 验证上传文档后 source object 存在。
- 验证处理完成后 `cleaned.md` object 存在。
- 验证正文读取成功。
- 验证删除 document 后 source 与 artifacts object 被清理。
- 验证 RustFS 不可用时不 fallback。

## 6. Open Questions

- Question：是否将 RustFS 集成测试纳入 CI？
  Impact：会影响 CI 环境依赖、执行时间和稳定性。
  Needed before：进入 CI 集成或发布验收阶段。
  Current decision：首期不纳入默认 Maven test。

- Question：是否需要自动创建 bucket？
  Impact：会影响应用启动职责和部署职责边界。
  Needed before：生产化部署前。
  Current decision：首期不自动创建，由部署或本地初始化负责。

- Question：是否需要历史 `data/ingest` 迁移？
  Impact：会影响 `s3` 模式下历史正文读取能力。
  Needed before：切换已有数据环境到 `s3` 模式前。
  Current decision：首期不迁移，后续单独 plan / spec。

## 7. Handoff to Issues

后续使用 `to-issues` 时，建议按 milestone 对应的 vertical slice 拆分：

1. 存储接入前置验证。
2. 配置与 S3 client 装配。
3. S3 source storage adapter。
4. S3 artifact storage adapter。
5. S3 模式端到端验收与默认 local 回归。
6. 发布文档与后续历史迁移准备。

每个 issue 应围绕一个可验证行为收口，不按“写配置 / 写类 / 写测试”做纯横向拆分。
