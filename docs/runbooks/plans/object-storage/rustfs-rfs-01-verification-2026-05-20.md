# RFS-01 RustFS 本地 S3 兼容访问验证记录

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认 RustFS 本地 endpoint、凭证、bucket 和基础 S3 兼容对象操作可用。
- RustFS endpoint：`http://localhost:9000`
- RustFS console：`http://localhost:9001`
- bucket：`myai-documents`
- smoke test key：`smoke/rfs-01.txt`

## 验证前提

- RustFS 容器：`myai-rustfs`
- 访问凭证：
  - access key：`admin`
  - secret key：按本地 `infra/docker-compose.yml` 配置使用
- bucket `myai-documents` 已通过 RustFS Web 控制台创建。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| RustFS 容器运行状态 | 通过，容器处于 `Up` 状态 |
| S3 endpoint 连通性 | 通过，匿名访问返回 `AccessDenied`，说明 endpoint 可达且需要鉴权 |
| bucket 可写入 | 通过，`PUT /myai-documents/smoke/rfs-01.txt` 返回 `200` |
| object 可读取 | 通过，`GET /myai-documents/smoke/rfs-01.txt` 返回 `200` |
| object 内容一致 | 通过，读取内容为 `rfs-01 smoke test 2026-05-20` |
| object 可删除 | 通过，`DELETE /myai-documents/smoke/rfs-01.txt` 返回 `204` |
| 删除后不可读取 | 通过，再次 `GET` 返回 `404 NoSuchKey` |

## 结论

RFS-01 已完成。当前本地 RustFS 可作为后续 RFS-02、RFS-03 和 RFS-04 的 S3 兼容对象存储验证环境。

本次 smoke test 使用 `smoke/rfs-01.txt`，未使用真实业务 prefix `source/...` 或 `artifacts/...`。

## 后续

- RFS-02 可以开始实现 `local | s3` 存储模式配置与 S3 Client 装配。
- 应用首期仍不负责自动创建 bucket；`myai-documents` 缺失应继续视为部署或本地初始化问题。
