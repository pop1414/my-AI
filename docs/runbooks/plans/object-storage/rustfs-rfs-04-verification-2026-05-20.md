# RFS-04 S3 Artifact Storage Adapter 完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认 `s3` 模式下处理产物可通过 `DocumentProcessingArtifactStorage` 端口写入、读取和按文档删除。
- 适配器：`S3DocumentProcessingArtifactStorage`
- 端口：`DocumentProcessingArtifactStorage`
- artifact prefix：`artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`
- 主链产物：`cleaned.md`

## 完成结论

RFS-04 已完成。当前应用在 `myai.ingest.storage.type=s3` 时已经具备 processing artifacts 的 S3 兼容对象存储适配器。

本阶段覆盖处理产物存储，尤其是版本级正文事实 `cleaned.md`。用户上传原文档 source 已在 RFS-03 完成，不属于本阶段新增范围。

## 改动范围

| 文件 | 说明 |
| --- | --- |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorage.java` | 新增 S3 artifact storage adapter |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorageTest.java` | 新增 S3 artifact adapter 单元测试 |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/config/S3StorageConfigurationTest.java` | 更新 `s3` 模式条件装配预期，确认 source 与 artifact 端口均使用 S3 adapter |

## 行为结果

`s3` 模式下，artifact object key 由 `DocumentStorageKeyResolver` 统一生成：

```text
artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}
```

示例：

```text
artifacts/default/documents/doc-001/versions/1/cleaned.md
```

当前实现行为：

- `saveVersion(...)` 强制写入 `cleaned.md`。
- `raw.xhtml` 按 `keepRawXhtml` 配置决定是否写入。
- `cleaned.html` 按 `keepCleanedHtml` 配置决定是否写入。
- `parse-result.json` 按 `keepParseResultJson` 配置决定是否写入。
- 同一版本同名 artifact 允许覆盖，支持处理重试写入完整结果。
- `loadVersionArtifact(...)` 缺失时返回 `Optional.empty()`。
- `loadVersionArtifact(...)` 先通过 `HeadObject` 检查对象大小，再读取正文。
- artifact 超过 `maxBytes` 时抛出 `DocumentVersionArtifactTooLargeException`，不会先完整读取对象内容。
- 删除文档时只清理 artifacts prefix，不触碰 source prefix。

## Acceptance Criteria 对照

| Acceptance criteria | 状态 | 说明 |
| --- | --- | --- |
| S3 artifact adapter 实现 `DocumentProcessingArtifactStorage` | 完成 | 新增 `S3DocumentProcessingArtifactStorage` |
| artifact object key 使用 `artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}` | 完成 | key 由 `DocumentStorageKeyResolver.resolveArtifactKey(...)` 生成 |
| `saveVersion(...)` 强制写入 `cleaned.md` | 完成 | `cleaned.md` 不受调试产物配置影响，始终写入 |
| `raw.xhtml`、`cleaned.html`、`parse-result.json` 按现有 artifacts 配置决定是否写入 | 完成 | 单元测试覆盖 `raw.xhtml` 和 `parse-result.json` 写入、`cleaned.html` 跳过 |
| 同一版本同名 artifact 允许覆盖，以支持处理重试写入完整结果 | 完成 | S3 `putObject` 默认覆盖同 key 当前版本对象 |
| `loadVersionArtifact(...)` 命中时返回 stable key、UTF-8 正文和字节长度 | 完成 | 单元测试覆盖 stable key、正文和 contentLength |
| object 不存在时，`loadVersionArtifact(...)` 返回空 | 完成 | 单元测试覆盖 `NoSuchKey` 返回空 |
| object 超过 `maxBytes` 时，抛出 `DocumentVersionArtifactTooLargeException` | 完成 | 单元测试覆盖异常中的 contentLength 和 maxBytes |
| 超过 `maxBytes` 的对象不得先完整读入内存再判断 | 完成 | 实现先 `HeadObject` 判断大小，单元测试确认不会调用 `getObjectAsBytes` |
| `loadVersionArtifact(...)` 不读取 source prefix、不触发重新解析、不从向量分块拼接正文 | 完成 | adapter 只按 artifact key 调用 S3，不接触 source、parser 或 vector store |
| `deleteByDocumentId(...)` 删除 artifacts prefix 下全部对象，并处理分页 list | 完成 | 单元测试覆盖两页 list 和批量 delete |
| artifacts 删除不影响 source prefix | 完成 | 删除 prefix 固定为 `artifacts/{workspaceId}/documents/{documentId}/` |
| 正文读取回归覆盖关键分支 | 完成 | 定向测试包含 `GetDocumentContentApplicationServiceTest`，完整 Maven 回归通过 |

## 验证结果

已执行以下验证命令：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=S3DocumentProcessingArtifactStorageTest,S3DocumentSourceStorageTest,S3StorageConfigurationTest,GetDocumentContentApplicationServiceTest" test
.\mvnw.cmd -q test
```

结果：

| 检查项 | 结果 |
| --- | --- |
| 项目编译 | 通过 |
| S3 artifact adapter 单元测试 | 通过 |
| S3 source adapter 回归测试 | 通过 |
| S3 存储模式条件装配测试 | 通过 |
| 正文读取应用层回归测试 | 通过 |
| 默认 local 模式完整回归测试 | 通过 |

## 当前边界

- RFS-04 只实现 artifact storage adapter，不新增 REST API。
- RFS-04 不改变正文读取 API、响应字段、权限规则或错误码。
- artifact 缺失仍由上层映射为 `CONTENT_ARTIFACT_MISSING` 或相关既有分支。
- artifact 过大仍由上层映射为 `CONTENT_TOO_LARGE`。
- 不从 source 实时解析正文。
- 不从 `vector_store` chunk 拼接完整正文。
- 不迁移历史 `data/ingest` 本地 artifacts。
- 不实现对象版本治理、生命周期策略或历史迁移。

## 后续

- RFS-05 可以开始做 S3 模式端到端验收。
- RFS-05 需要验证上传后 source object 存在、处理后 `cleaned.md` object 存在、正文读取成功、删除后 source 与 artifacts 均被清理。
- RFS-05 还需要验证 RustFS 不可用时不会 fallback 到本地文件系统。
