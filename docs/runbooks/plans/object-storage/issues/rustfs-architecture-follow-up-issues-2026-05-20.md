# RustFS 架构后续 Issue 草案

## 生成信息

- 生成日期：2026-05-20
- 当前状态：核心修复已落地，未发布 GitHub Issues
- 来源：RustFS 接入后的 architecture review
- 关联 ADR：
    - `docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`
    - `docs/adr/ADR-0006-document-version-read-boundary.md`
- 关联上下文：`CONTEXT.md`
- 说明：本文件用于记录 RustFS 首期完成后的后续架构 deepening opportunity。当前没有单独 FS / TS Spec；按用户要求先以本地 issue 草案沉淀，不上传 GitHub。

## 总览

| 本地编号  | GitHub Issue | 标题                             | 类型 | 标签建议          | 阻塞   |
| --------- | ------------ | -------------------------------- | ---- | ----------------- | ------ |
| RFS-FU-01 | 未发布       | 收口上传受理与 source 保存一致性 | AFK  | `ready-for-agent` | RFS-05 |

## Issue 草案

### RFS-FU-01 收口上传受理与 source 保存一致性

GitHub Issue：未发布

Type：AFK

Blocked by：RFS-05

#### Source

- ADR：`docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`
- ADR coverage：
    - RustFS 不可用时不做本地 fallback。
    - source 与 artifacts 通过存储端口访问，业务层不依赖具体对象存储产品。
    - 首期 `s3` 模式覆盖新上传 source 与新生成 artifacts。
- CONTEXT coverage：
    - ingest 上传受理、source 保存和异步处理链路共同构成文档资产生命周期。
    - RustFS 不可用时上传、处理或正文读取应进入明确失败分支，避免 source 与 artifacts 分散写入多个存储介质。
- Architecture review note：
    - 当前首传 source 保存发生在 `DocumentIngestController`，DB document/version fact 创建发生在 `AcceptUploadApplicationService`。
    - 该拆分会让上传受理 module 的 interface 无法完整表达“受理上传必须同时形成 source”。

#### What to build

将首次上传的 source 保存从接口层收口到上传受理 application module 中，使 `AcceptUploadUseCase` 负责完整的上传受理一致性：权限校验、document/version fact 创建或复用、source 保存、失败语义和审计记录。

完成后，Controller 只负责 HTTP multipart 输入、基础参数转换和响应映射，不再直接调用 `DocumentSourceStorage`。RustFS 不可用、bucket 缺失、凭证错误或 source 保存失败时，上传受理应明确失败，不留下可被 worker 后续处理但缺少 source 的半成品 document。

#### Problem

当前链路为：

```text
DocumentIngestController.upload(...)
  -> acceptUploadUseCase.handle(command)
       -> 创建或复用 document/version DB fact
       -> 记录审计
       -> 返回 UploadTicket
  -> documentSourceStorage.save(uploadTicket.documentId(), filename, fileBytes)
```

该顺序存在两个风险：

- DB fact 已创建但 RustFS source 写入失败时，可能留下 `UPLOADED` document，worker 后续读取 source 失败。
- 同内容重复上传复用既有 `documentId` 时，如果本次 filename 与既有 version filename 不同，Controller 仍可能按本次 filename 写入一个未被 version fact 引用的 source key。

#### Spec coverage

- FS：
    - 上传受理成功必须表示 document/version fact 与对应 source 已形成一致状态。
    - source 保存失败时，上传受理不得静默成功，也不得 fallback 到本地文件系统。
    - 同内容复用既有 document 时，不应因为本次 filename 不同而创建未被 version fact 引用的 source object。
- TS：
    - `AcceptUploadUseCase` 或其 command 应携带保存 source 所需的内容输入。
    - `AcceptUploadApplicationService` 注入并调用 `DocumentSourceStorage`，Controller 不直接依赖该 storage port。
    - 首选策略为 DB fact 与 source 保存处于同一个 application transaction 流程内；source 保存异常向外抛出并触发 DB 事务回滚。
    - 如果选择支持复用场景 source 自愈，只能按既有 version fact 的 filename/key 修复，不得使用本次上传 filename 生成额外 source key。

#### Acceptance criteria

- [x] `DocumentIngestController` 不再直接依赖或调用 `DocumentSourceStorage`。
- [x] 首次上传新 document 成功时，application module 同时完成 document/version fact 创建和 version 1 source 保存。
- [x] source 保存失败时，上传接口返回失败，DB 不保留新的 `UPLOADED` document 半成品。
- [x] `s3` 模式 source 保存失败时不 fallback 到 `data/ingest`。
- [x] 同内容重复上传并复用 existing document 时，不使用本次上传 filename 写入额外 source object。
- [x] 如实现复用 source 自愈，只按 existing version fact 中的 filename 和 versionNumber 保存 source。
- [x] 上传受理失败审计能区分 source 保存失败与业务校验失败。
- [x] 单元测试覆盖新 document source 保存成功、source 保存失败回滚、重复上传不同 filename 不产生 orphan source key。
- [x] S3 adapter 异常路径测试或 application 测试覆盖 RustFS 不可用时上传受理不留下半成品 document。

#### 实现记录

- `AcceptUploadCommand` 已携带 `sourceContent`。
- `AcceptUploadApplicationService` 已注入 `DocumentSourceStorage`，新 document 创建后同步保存 version 1 source。
- `DocumentIngestController` 已移除 `DocumentSourceStorage` 依赖，只把 multipart 内容传入上传受理用例。
- 同内容复用 existing document 时，上传受理用例直接返回既有 `documentId`，不按本次 filename 写入 source。
- source 保存失败会向上抛出异常；由于上传受理用例处于 `@Transactional` 中，数据库 document fact 会随异常回滚。
- 上传受理失败审计已落地：业务校验失败记录为 `UPLOAD_BUSINESS_VALIDATION_FAILED`，source 保存失败记录为 `UPLOAD_SOURCE_SAVE_FAILED`。
- `JdbcAuditEventRepository.save` 使用 `REQUIRES_NEW` 独立事务，确保 source 保存异常触发上传受理主事务回滚时，失败审计仍可保留。

#### Blocked by

- RFS-05

## 审阅提示

- 先确认该 issue 是否应先补一份轻量 FS / TS Spec；当前文件只是本地 issue 草案。
- 重点审阅上传受理 module 的 interface：caller 是否还能绕过 application module 单独写 source。
- 重点审阅同内容复用路径：不要让不同 filename 的重复上传制造未被 version fact 引用的 object key。
- 重点审阅事务与补偿策略：RustFS 写入不能参与数据库分布式事务，因此必须明确失败后 DB fact 与对象残留的取舍。
