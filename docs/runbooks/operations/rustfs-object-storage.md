# RustFS 文档资产存储运行手册

## 目的

本文说明如何在本项目中使用 RustFS 作为 `S3-compatible document asset storage` 的部署实现。

RustFS 只承载文档 source 与 artifacts 的存储介质，不改变 ingest 领域语义：

- source 仍通过 `DocumentSourceStorage` 访问。
- artifacts 仍通过 `DocumentProcessingArtifactStorage` 访问。
- `cleaned.md` 仍是版本级 artifact。

## 运行前提

- 已安装 Docker 或等价容器运行环境。
- 后端配置可访问 RustFS endpoint。
- 已准备访问凭证。
- 已创建 bucket：`myai-documents`。

本地开发默认使用：

- image：`rustfs/rustfs:latest`
- container：`myai-rustfs`
- endpoint：`http://localhost:9000`
- console：`http://localhost:9001`
- access key：`admin`
- secret key：`Admin@123`
- bucket：`myai-documents`

## Docker Compose

当前本地开发环境使用 `infra/docker-compose.yml` 中的 `rustfs` 服务：

```yaml
services:
  rustfs:
    image: rustfs/rustfs:latest
    container_name: myai-rustfs
    environment:
      RUSTFS_ACCESS_KEY: admin
      RUSTFS_SECRET_KEY: Admin@123
      TZ: Asia/Shanghai
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - rustfs_data:/data
    restart: always
```

启动后应确认 9000 端口可用于 S3 API，9001 端口可用于控制台访问。生产或共享环境不得继续使用本地默认凭证。

## 推荐配置

后端配置项建议通过环境变量注入：

```yaml
myai:
  ingest:
    storage:
      type: ${INGEST_STORAGE_TYPE:local}
      root-dir: ${INGEST_STORAGE_ROOT_DIR:data/ingest}
      s3:
        endpoint: ${INGEST_STORAGE_S3_ENDPOINT:http://localhost:9000}
        bucket: ${INGEST_STORAGE_S3_BUCKET:myai-documents}
        region: ${INGEST_STORAGE_S3_REGION:us-east-1}
        access-key: ${INGEST_STORAGE_S3_ACCESS_KEY:admin}
        secret-key: ${INGEST_STORAGE_S3_SECRET_KEY:Admin@123}
        path-style-access: ${INGEST_STORAGE_S3_PATH_STYLE_ACCESS:true}
      artifacts:
        max-read-bytes: ${INGEST_STORAGE_ARTIFACT_MAX_READ_BYTES:2000000}
        keep-raw-xhtml: ${INGEST_STORAGE_KEEP_RAW_XHTML:false}
        keep-cleaned-html: ${INGEST_STORAGE_KEEP_CLEANED_HTML:false}
        keep-parse-result-json: ${INGEST_STORAGE_KEEP_PARSE_RESULT_JSON:true}
```

配置说明：

- `myai.ingest.storage.type` 默认为 `local`；只有显式设置为 `s3` 时才启用 S3 兼容对象存储。
- `myai.ingest.storage.root-dir` 仅在 `local` 模式使用，默认 `data/ingest`。
- `myai.ingest.storage.s3.endpoint`、`bucket`、`region`、`access-key`、`secret-key`、`path-style-access` 是 S3 模式的连接配置。
- `myai.ingest.storage.artifacts.max-read-bytes` 控制正文 artifact 读取上限，超出后返回既有 `CONTENT_TOO_LARGE` 语义。
- `keep-raw-xhtml`、`keep-cleaned-html`、`keep-parse-result-json` 控制可选调试产物是否保留；`cleaned.md` 始终写入。

首期默认仍使用 `local`。需要启用 RustFS 时设置：

```powershell
$env:INGEST_STORAGE_TYPE = "s3"
$env:INGEST_STORAGE_S3_ENDPOINT = "http://localhost:9000"
$env:INGEST_STORAGE_S3_BUCKET = "myai-documents"
$env:INGEST_STORAGE_S3_REGION = "us-east-1"
$env:INGEST_STORAGE_S3_ACCESS_KEY = "<access-key>"
$env:INGEST_STORAGE_S3_SECRET_KEY = "<secret-key>"
$env:INGEST_STORAGE_S3_PATH_STYLE_ACCESS = "true"
```

## 发布边界

首期 `s3` 模式只覆盖切换后新上传的 source 与新生成的 artifacts。

- 切换到 `s3` 不会自动迁移既有 `data/ingest` 本地历史文件。
- S3 模式不会双读本地文件系统，也不会在 artifact 缺失时回退到 source 重新解析。
- RustFS 不可用时，上传、处理、读取或删除应进入明确失败分支，不得 fallback 到本地文件系统。
- PostgreSQL 只保存业务事实、版本状态和向量检索数据，不保存 source/artifacts 对象内容。
- PostgreSQL 备份不能替代 RustFS 备份；恢复方案必须同时覆盖数据库与对象存储。

## Bucket 与 Key 规则

首期使用单 bucket：

- bucket：`myai-documents`

对象 key：

- source：`source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`
- artifact：`artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`

示例：

```text
source/default/documents/018f.../versions/1/example.pdf
artifacts/default/documents/018f.../versions/1/cleaned.md
```

## Bucket 初始化

首期应用不负责自动创建 bucket。进入 `s3` 模式前，部署或本地初始化流程必须先创建 `myai-documents`。

初始化后至少完成一次非业务 key 的 put / get / delete smoke test。推荐使用临时 key，例如：

```text
smoke/rfs-setup-check.txt
```

如果 bucket 缺失，后端启动或首次访问对象存储时会暴露为部署前提问题。不要通过切回本地写入来绕过该错误，否则会导致同一环境的 source 与 artifacts 分散在不同存储介质。

## 健康检查

启动后至少确认：

- RustFS 服务端口可访问。
- bucket `myai-documents` 已存在。
- 后端启动时 S3 client 配置无鉴权错误。
- 上传一份测试文档后，RustFS 中出现 source object。
- 文档处理完成后，RustFS 中出现 `cleaned.md`。
- 正文读取接口能读取对应版本正文。

## RFS-05 验收

RFS-05 用于确认 `s3` 模式下 source、版本级 `cleaned.md`、正文读取错误语义、删除清理和默认 `local` 回归均可用。真实 RustFS 验收不进入默认 Maven test，避免让普通回归依赖外部对象存储服务。

### 自动 smoke test

启动 RustFS 并确保 `myai-documents` bucket 已存在后，执行：

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

该 smoke test 会使用临时 `rfs05-smoke-*` documentId 验证：

- 写入 source object：`source/default/documents/{documentId}/versions/1/rfs05-smoke.txt`。
- 写入并读取版本级 `cleaned.md` object：`artifacts/default/documents/{documentId}/versions/1/cleaned.md`。
- artifact 缺失时由 adapter 返回空，供应用层保持 `CONTENT_ARTIFACT_MISSING` 映射。
- artifact 超过读取上限时抛出 `DocumentVersionArtifactTooLargeException`，供应用层保持 `CONTENT_TOO_LARGE` 映射。
- 删除后 source 与 artifacts prefix 下的 smoke 对象均不存在。

默认回归仍应执行：

```powershell
.\mvnw.cmd -q test
```

### 手工 API 验收

当需要走完整 REST 链路时，按以下顺序验收：

1. 设置 `INGEST_STORAGE_TYPE=s3` 及上述 S3 环境变量，启动后端。
2. 通过既有 `POST /api/v1/documents/upload` 上传测试文档。
3. 在 RustFS 中确认 source key 存在。
4. 等待 worker 处理完成，或通过既有处理链路推进到 `INDEXED`。
5. 在 RustFS 中确认版本级 `cleaned.md` key 存在。
6. 调用 `GET /api/v1/documents/{documentId}/content?source=LATEST`，确认响应字段、状态码和本地模式一致。
7. 临时移除目标 `cleaned.md` 后读取正文，确认仍返回 `CONTENT_ARTIFACT_MISSING`，且不会从 source 重新解析。
8. 将目标 `cleaned.md` 替换为超过 `INGEST_STORAGE_ARTIFACT_MAX_READ_BYTES` 的对象后读取正文，确认返回 `CONTENT_TOO_LARGE`。
9. 调用 `DELETE /api/v1/documents/{documentId}`，确认 source 与 artifacts object 均被清理。
10. 停止 RustFS 后重复上传、处理、读取或删除操作，确认请求失败且不会写入 `data/ingest`。

## 常见问题

### 连接失败

检查：

- `INGEST_STORAGE_S3_ENDPOINT` 是否正确。
- RustFS 容器是否启动。
- 后端所在网络是否能访问 RustFS 服务端口。

### 403 或鉴权失败

检查：

- access key / secret key 是否与 RustFS 中配置一致。
- bucket 权限是否允许读写。
- 后端环境变量是否生效。

### bucket 不存在

首期应用不负责自动创建 bucket。部署前应显式创建 `myai-documents`。

### RustFS 不可用后本地目录没有新文件

这是预期行为。`s3` 模式下不允许 fallback 到 `data/ingest`，否则同一 document 的 source 与 artifacts 会分散到不同存储介质，后续删除、备份和恢复都会失去一致边界。

### 正文读取返回 `CONTENT_ARTIFACT_MISSING`

说明目标版本的 `cleaned.md` 未命中。检查：

- 处理任务是否成功执行。
- artifact key 是否符合约定。
- 当前运行模式是否为 `s3`。
- 是否正在读取首期接入前产生的本地历史文件。

### 切换到 S3 后历史文档无法读取

这是首期预期边界。既有 `data/ingest` 历史文件不会自动迁移到 RustFS。需要通过单独迁移计划处理。

后续迁移入口见：`docs/runbooks/plans/object-storage/rustfs-history-migration-plan.md`。

## 回滚

如果 RustFS 接入出现阻塞，可将后端切回本地存储：

```powershell
$env:INGEST_STORAGE_TYPE = "local"
```

注意：

- 回滚只影响切换后的新写入路径。
- 已写入 RustFS 的对象不会自动复制回本地文件系统。
- 回滚后，新写入会回到 `INGEST_STORAGE_ROOT_DIR`；回滚前写入 RustFS 的 source/artifacts 仍需通过 RustFS 备份保留。
- 历史数据一致性需要按迁移计划单独处理。

## 备份

RustFS 承载 source 与 artifacts 对象内容，必须单独纳入备份和恢复演练。

备份原则：

- PostgreSQL 备份只覆盖业务事实、版本状态、权限、审计和向量数据，不能恢复 RustFS 中的 source/artifacts。
- RustFS 数据目录或 RustFS 推荐的冗余部署方式必须与 PostgreSQL 备份一起设计恢复点。
- 恢复演练应同时验证数据库记录、source object、版本级 `cleaned.md` object 和正文读取接口。
- 只恢复 PostgreSQL 而不恢复 RustFS 时，历史 document 可能存在元数据但缺少 source/artifacts，正文读取会进入既有缺失语义。

## 运维注意事项

- 不要把 access key 和 secret key 写入仓库。
- 生产或长期演示环境应配置持久化卷。
- 生产或共享环境应启用 TLS 或放在可信内网后面。
- 定期备份 RustFS 数据目录或使用 RustFS 推荐的冗余部署方式。
- 不要让应用在 RustFS 不可用时 fallback 到本地文件系统，避免 source 与 artifacts 分散在不同存储介质。
