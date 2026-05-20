# RFS-02 存储模式配置与 S3 Client 装配完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认应用可以通过配置在 `local` 与 `s3` 存储模式之间切换，并在 `s3` 模式下创建 S3 client。
- 默认存储模式：`local`
- S3 模式配置入口：`myai.ingest.storage.type=s3`
- S3 配置前缀：`myai.ingest.storage.s3`

## 完成结论

RFS-02 已完成。当前应用已经具备存储模式配置、S3 连接参数配置、S3 client 装配和 S3 模式必填配置启动校验能力。

本次不实现 source 或 artifact 的 S3 读写逻辑，不改变上传、处理、正文读取或删除 API。真正的 S3 source storage adapter 和 S3 artifact storage adapter 分别进入 RFS-03 和 RFS-04。

## 改动范围

| 文件                                                                                                           | 说明                                                             |
| -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `pom.xml`                                                                                                      | 引入 AWS SDK v2 的 S3 client、Apache HTTP client 和 BOM 版本管理 |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java`                        | 新增 local                                                       |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/config/S3StorageConfiguration.java`                  | 新增 S3 client 条件装配和必填配置校验                            |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentSourceStorage.java`             | 本地 source storage 仅在 `local` 模式装配                        |
| `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorage.java` | 本地 artifact storage 仅在 `local` 模式装配                      |
| `src/main/resources/application.yaml`                                                                          | 增加 storage type 和 S3 配置示例                                 |
| `src/test/java/io/github/spike/myai/ingest/infrastructure/config/S3StorageConfigurationTest.java`              | 增加配置与条件装配测试                                           |

## 配置结果

默认配置仍为本地模式：

```yaml
myai:
    ingest:
        storage:
            type: ${INGEST_STORAGE_TYPE:local}
            root-dir: ${INGEST_STORAGE_ROOT_DIR:data/ingest}
```

S3 模式配置入口：

```yaml
myai:
    ingest:
        storage:
            type: s3
            s3:
                endpoint: http://localhost:9000
                bucket: myai-documents
                region: us-east-1
                access-key: admin
                secret-key: Admin@123
                path-style-access: true
```

配置字段使用 `s3` 命名，不使用 `rustfs` 作为业务配置名。RustFS 仍只是当前阶段的 S3 兼容对象存储部署实现。

## Acceptance Criteria 对照

| Acceptance criteria                                                             | 状态 | 说明                                                                                                      |
| ------------------------------------------------------------------------------- | ---- | --------------------------------------------------------------------------------------------------------- |
| 新增 `myai.ingest.storage.type`，默认值为 `local`                               | 完成 | `IngestProperties.Storage.type` 默认值为 `LOCAL`，`application.yaml` 默认读取 `INGEST_STORAGE_TYPE:local` |
| `type=local` 时不要求 S3 endpoint、bucket、access key、secret key               | 完成 | 默认 local 模式不创建 `S3Client`，也不校验 S3 必填项                                                      |
| `type=s3` 时会创建 S3 client                                                    | 完成 | `S3StorageConfiguration` 仅在 `myai.ingest.storage.type=s3` 时创建 `S3Client`                             |
| `type=s3` 且必填 S3 配置缺失时，应用启动失败，并能定位缺失配置项                | 完成 | 缺失配置时抛出带配置项名称的启动异常，例如 `myai.ingest.storage.s3.endpoint`                              |
| S3 配置包含 endpoint、bucket、region、access key、secret key、path-style access | 完成 | `IngestProperties.S3` 已包含这些字段                                                                      |
| 配置字段不使用 `rustfs` 作为配置命名                                            | 完成 | 配置前缀为 `myai.ingest.storage.s3`                                                                       |
| 条件装配不会同时产生 local 和 S3 两套同类型 storage bean                        | 完成 | 本地 source/artifact storage 只在 `local` 模式装配；RFS-02 阶段 `s3` 模式只提供 `S3Client`                |
| 默认 `local` 模式下，现有本地存储测试保持通过                                   | 完成 | 完整 Maven 测试已通过                                                                                     |
| `domain` 和 `application` 代码不依赖 AWS SDK                                    | 完成 | AWS SDK 依赖只出现在 infrastructure 配置层                                                                |

## 验证结果

已执行以下验证命令：

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=S3StorageConfigurationTest" test
.\mvnw.cmd -q test
```

结果：

| 检查项                            | 结果                             |
| --------------------------------- | -------------------------------- |
| 项目编译                          | 通过                             |
| RFS-02 配置测试                   | 通过                             |
| 默认 local 模式完整回归测试       | 通过                             |
| 默认 local 模式是否创建 S3 client | 未创建，符合预期                 |
| 有效 s3 配置是否创建 S3 client    | 已创建，符合预期                 |
| s3 缺失必填配置是否启动失败       | 已失败并指出缺失配置项，符合预期 |

## 测试日志说明

执行 `S3StorageConfigurationTest` 时会出现类似日志：

```text
Error creating bean with name 'documentAssetS3Client' ...
myai.ingest.storage.s3.endpoint must be configured when myai.ingest.storage.type=s3
```

这是测试故意触发的失败场景，用于验证 `type=s3` 且缺少必填配置时应用会在启动阶段失败。该日志不是 RFS-02 实现失败。

## 当前边界

- RFS-02 只创建 S3 client，不进行真实 S3 put / get / delete。
- `s3` 模式下 source storage adapter 尚未实现，进入 RFS-03。
- `s3` 模式下 artifact storage adapter 尚未实现，进入 RFS-04。
- 不迁移历史 `data/ingest` 本地文件。
- 不新增 REST API。
- 不改变正文读取语义。

## 后续

- RFS-03 可以开始实现 S3 source storage adapter。
- RFS-04 在 RFS-03 基础上实现 S3 artifact storage adapter 和正文读取闭环。
