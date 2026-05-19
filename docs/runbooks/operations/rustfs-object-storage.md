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

## 推荐配置

后端配置项建议通过环境变量注入：

```yaml
myai:
  ingest:
    storage:
      type: ${INGEST_STORAGE_TYPE:local}
      s3:
        endpoint: ${INGEST_STORAGE_S3_ENDPOINT:http://localhost:9000}
        bucket: ${INGEST_STORAGE_S3_BUCKET:myai-documents}
        region: ${INGEST_STORAGE_S3_REGION:us-east-1}
        access-key: ${INGEST_STORAGE_S3_ACCESS_KEY:}
        secret-key: ${INGEST_STORAGE_S3_SECRET_KEY:}
        path-style-access: ${INGEST_STORAGE_S3_PATH_STYLE_ACCESS:true}
```

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

## 健康检查

启动后至少确认：

- RustFS 服务端口可访问。
- bucket `myai-documents` 已存在。
- 后端启动时 S3 client 配置无鉴权错误。
- 上传一份测试文档后，RustFS 中出现 source object。
- 文档处理完成后，RustFS 中出现 `cleaned.md`。
- 正文读取接口能读取对应版本正文。

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

### 正文读取返回 `CONTENT_ARTIFACT_MISSING`

说明目标版本的 `cleaned.md` 未命中。检查：

- 处理任务是否成功执行。
- artifact key 是否符合约定。
- 当前运行模式是否为 `s3`。
- 是否正在读取首期接入前产生的本地历史文件。

### 切换到 S3 后历史文档无法读取

这是首期预期边界。既有 `data/ingest` 历史文件不会自动迁移到 RustFS。需要通过单独迁移计划处理。

## 回滚

如果 RustFS 接入出现阻塞，可将后端切回本地存储：

```powershell
$env:INGEST_STORAGE_TYPE = "local"
```

注意：

- 回滚只影响切换后的新写入路径。
- 已写入 RustFS 的对象不会自动复制回本地文件系统。
- 历史数据一致性需要按迁移计划单独处理。

## 运维注意事项

- 不要把 access key 和 secret key 写入仓库。
- 生产或长期演示环境应配置持久化卷。
- 生产或共享环境应启用 TLS 或放在可信内网后面。
- 定期备份 RustFS 数据目录或使用 RustFS 推荐的冗余部署方式。
- 不要让应用在 RustFS 不可用时 fallback 到本地文件系统，避免 source 与 artifacts 分散在不同存储介质。
