# RFS-03 S3 Source Storage Adapter 完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认 `s3` 模式下 source 文件可通过 `DocumentSourceStorage` 端口写入、读取、幂等保存和按文档删除。
- 适配器：`S3DocumentSourceStorage`
- 端口：`DocumentSourceStorage`
- source prefix：`source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`

## 完成结论

RFS-03 已完成。当前应用在 `myai.ingest.storage.type=s3` 时已经具备 source 文件的 S3 兼容对象存储适配器。

本阶段只处理用户上传原文档 source 的存储，不处理 `cleaned.md`、`raw.xhtml`、`cleaned.html`、`parse-result.json` 等处理产物。artifact 存储进入 RFS-04。

## 改动范围

| 文件 | 说明 |
| --- | --- |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentSourceStorage.java` | 新增 S3 source storage adapter |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentSourceStorageTest.java` | 新增 S3 source adapter 单元测试 |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/config/S3StorageConfigurationTest.java` | 更新 `s3` 模式条件装配预期，确认 source 端口使用 S3 adapter |

## 行为结果

`s3` 模式下，source 文件 object key 由 `DocumentStorageKeyResolver` 统一生成：

```text
source/default/documents/{documentId}/versions/{versionNumber}/{filename}
```

示例：

```text
source/default/documents/doc-001/versions/1/example.pdf
```

当前实现行为：

- `save(documentId, filename, content)` 等价写入 version 1。
- `saveVersion(documentId, versionNumber, filename, content)` 复用幂等保存逻辑。
- `saveVersionIfAbsent(...)` 首次写入返回 `true`。
- 同一 key 内容一致时返回 `false`，表示幂等命中。
- 同一 key 内容不一致时抛出稳定异常类型：`DocumentSourceContentConflictException`。
- `load(...)` 读取 version 1 source。
- `loadVersion(...)` 读取指定版本 source。
- source 缺失时返回 `Optional.empty()`。
- 删除文档时只清理 source prefix，不触碰 artifacts prefix。

## Acceptance Criteria 对照

| Acceptance criteria | 状态 | 说明 |
| --- | --- | --- |
| S3 source adapter 实现 `DocumentSourceStorage` | 完成 | 新增 `S3DocumentSourceStorage` |
| source object key 使用 `source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}` | 完成 | key 由 `DocumentStorageKeyResolver.resolveSourceKey(...)` 生成 |
| `save(documentId, filename, content)` 等价写入 version 1 | 完成 | `save(...)` 委托 `saveVersion(..., 1, ...)` |
| `saveVersion(...)` 通过 `saveVersionIfAbsent(...)` 保持幂等保存契约 | 完成 | `saveVersion(...)` 委托 `saveVersionIfAbsent(...)` |
| 首次 `saveVersionIfAbsent(...)` 写入成功后返回 `true` | 完成 | 单元测试覆盖对象不存在时写入成功 |
| 同一 key 内容一致时，`saveVersionIfAbsent(...)` 返回 `false` | 完成 | 单元测试覆盖幂等命中 |
| 同一 key 内容不一致时，抛出稳定冲突异常类型 `DocumentSourceContentConflictException` | 完成 | 单元测试覆盖冲突异常类型 |
| `load` / `loadVersion` 命中时返回完整字节，未命中时返回空 | 完成 | 单元测试覆盖命中读取和缺失读取 |
| `load` / `loadVersion` 不 fallback 到本地文件系统或旧 document 级路径 | 完成 | S3 adapter 只通过 S3 client 读取 object |
| `deleteByDocumentId` 删除 source prefix 下全部对象，并处理分页 list | 完成 | 单元测试覆盖两页 list 和批量 delete |
| source 删除不影响 artifacts prefix | 完成 | 删除 prefix 固定为 `source/default/documents/{documentId}/` |
| 单元测试覆盖版本隔离、幂等命中、冲突、缺失读取和 prefix 删除 | 完成 | 覆盖幂等、冲突、缺失读取、source key 和分页删除；版本隔离通过 key 中 versionNumber 保证并由读取测试覆盖 |

## 验证结果

已执行以下验证命令：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=S3DocumentSourceStorageTest,S3StorageConfigurationTest" test
.\mvnw.cmd -q test
```

结果：

| 检查项 | 结果 |
| --- | --- |
| 项目编译 | 通过 |
| S3 source adapter 单元测试 | 通过 |
| S3 存储模式条件装配测试 | 通过 |
| 默认 local 模式完整回归测试 | 通过 |

## 当前边界

- RFS-03 只处理 source 文件，不处理 processing artifacts。
- `cleaned.md` 仍未在 `s3` 模式下写入对象存储，进入 RFS-04。
- 仍不迁移历史 `data/ingest` 本地 source。
- 不新增源文件下载能力。
- 不新增 REST API。
- 不把完整 object key 落库。
- 首期不依赖 RustFS 条件写入能力；并发同 key 写入主要通过版本分配和内容比对降低风险。

## 后续

- RFS-04 实现 S3 artifact storage adapter。
- RFS-04 需要确保 `cleaned.md` 强制写入，并保持正文读取只读目标版本 artifact。
- RFS-04 需要实现 artifact 读取大小限制、缺失返回空和 artifacts prefix 分页删除。
