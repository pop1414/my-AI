# RFS-05 S3 模式端到端验收与 local 回归完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认 `s3` 模式具备上传 source、生成/读取 `cleaned.md`、删除清理和失败不回退的验收入口，同时默认 `local` 回归不受影响。
- 真实服务入口：`S3DocumentStorageSmokeTest`
- 默认回归入口：`.\mvnw.cmd -q test`
- Runbook：`docs/runbooks/operations/rustfs-object-storage.md`

## 完成结论

RFS-05 已完成代码侧和文档侧验收闭环。真实 RustFS 验收以显式环境变量 `MYAI_RUSTFS_SMOKE_TEST=true` 启用，不纳入默认 Maven test；默认配置已确认保持 `local`，用于保护本地文件系统回归。

## 改动范围

| 文件 | 说明 |
| --- | --- |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentStorageSmokeTest.java` | 新增显式启用的 RustFS 真实服务 smoke test |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentSourceStorageTest.java` | 补充 S3 source 存储异常向上抛出的不回退测试 |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorageTest.java` | 补充 S3 artifact 存储异常向上抛出的不回退测试 |
| `docs/runbooks/operations/rustfs-object-storage.md` | 补充 RFS-05 自动 smoke test 与手工 API 验收步骤 |
| `docs/runbooks/plans/object-storage/rustfs-rfs-05-verification-2026-05-20.md` | 新增 RFS-05 完成概览 |

## Acceptance Criteria 对照

| Acceptance criteria | 状态 | 说明 |
| --- | --- | --- |
| `s3` 模式下上传文档后，RustFS 中存在对应 source object | 完成 | smoke test 通过 `S3DocumentSourceStorage.save(...)` 写入并读取 source key |
| `s3` 模式下处理完成后，RustFS 中存在对应版本级 `cleaned.md` object | 完成 | smoke test 通过 `S3DocumentProcessingArtifactStorage.saveVersion(...)` 写入并读取 `cleaned.md` |
| `s3` 模式下正文读取成功，响应字段和错误码与本地模式一致 | 完成 | adapter smoke 覆盖 `cleaned.md` 读取；应用层字段与错误码由既有 `GetDocumentContentApplicationServiceTest` 覆盖 |
| artifact 缺失仍返回 `CONTENT_ARTIFACT_MISSING`，不触发 source 重新解析 | 完成 | S3 adapter 缺失返回空；应用层缺失映射由正文读取回归覆盖 |
| artifact 超过读取上限仍返回 `CONTENT_TOO_LARGE` | 完成 | smoke test 覆盖过大 artifact 抛 `DocumentVersionArtifactTooLargeException`；应用层映射由正文读取回归覆盖 |
| 删除 document 后，source 与 artifacts object 都被清理 | 完成 | smoke test 在 finally 中执行 source/artifacts 删除并断言对象不存在 |
| RustFS 不可用时，上传、处理、读取或删除不 fallback 到本地文件系统 | 完成 | S3 adapter 不依赖 local storage；单元测试覆盖 S3 存储异常向上抛出；默认和 S3 条件装配测试确认两套 storage bean 不会并存 |
| 默认 `local` 模式回归通过 | 完成 | `application.yaml` 默认值已确认保持 `local`；默认 Maven test 覆盖 local 模式 |
| 验收步骤沉淀到 runbook 或测试说明中 | 完成 | Runbook 新增 RFS-05 验收章节 |

## 验收命令

已执行默认 local 回归：

```powershell
.\mvnw.cmd -q test
```

结果：通过。

已执行真实 RustFS smoke test：

```powershell
$env:MYAI_RUSTFS_SMOKE_TEST = "true"
$env:INGEST_STORAGE_S3_ENDPOINT = "http://localhost:9000"
$env:INGEST_STORAGE_S3_BUCKET = "myai-documents"
$env:INGEST_STORAGE_S3_REGION = "us-east-1"
$env:INGEST_STORAGE_S3_ACCESS_KEY = "admin"
$env:INGEST_STORAGE_S3_SECRET_KEY = "Admin@123"
$env:INGEST_STORAGE_S3_PATH_STYLE_ACCESS = "true"
.\mvnw.cmd -q "-Dtest=S3DocumentStorageSmokeTest" test
```

结果：通过。

已执行定向回归：

```powershell
.\mvnw.cmd -q "-Dtest=S3DocumentStorageSmokeTest,S3StorageConfigurationTest,S3DocumentSourceStorageTest,S3DocumentProcessingArtifactStorageTest,GetDocumentContentApplicationServiceTest" test
```

结果：通过。

## 当前边界

- RFS-05 不新增 REST API。
- 真实 RustFS 测试不进入默认 CI / Maven test。
- 首期仍不迁移历史 `data/ingest`。
- RustFS 不可用时保持失败，不做本地文件系统 fallback。
- 完整 REST 链路验收依赖 PostgreSQL、认证上下文、worker 和模型配置，按 runbook 手工执行。
