# RFS-01 RustFS 本地 S3 兼容访问完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：确认 RustFS 本地 endpoint、凭证、bucket 和基础 S3 兼容对象操作可用。
- RustFS endpoint：`http://localhost:9000`
- RustFS console：`http://localhost:9001`
- bucket：`myai-documents`
- smoke test key：`smoke/rfs-01.txt`

## 完成结论

RFS-01 已完成。当前本地 RustFS 可作为后续 RFS-02、RFS-03 和 RFS-04 的 S3 兼容对象存储验证环境。

本次验证未实现后端 storage adapter，未修改业务读取语义，未使用真实业务 prefix `source/...` 或 `artifacts/...`。

## 验证前提

- RustFS 容器：`myai-rustfs`
- 访问凭证：
  - access key：`admin`
  - secret key：按本地 `infra/docker-compose.yml` 配置使用
- bucket `myai-documents` 已通过 RustFS Web 控制台创建。

## Acceptance Criteria 对照

| Acceptance criteria | 状态 | 说明 |
| --- | --- | --- |
| RustFS endpoint 可访问 | 完成 | `http://localhost:9000` 可访问；匿名访问返回 `AccessDenied`，说明服务正常且需要鉴权 |
| 当前配置中的 access key / secret key 可用于 S3 兼容访问 | 完成 | 使用本地配置凭证完成 SigV4 签名请求 |
| `myai-documents` bucket 已创建或已有明确初始化步骤 | 完成 | bucket 已通过 RustFS Web 控制台创建；runbook 已说明首期应用不负责自动创建 bucket |
| smoke test 可完成 put / get / delete 基础对象操作 | 完成 | `PUT=200`、`GET=200`、`DELETE=204` |
| smoke test 使用非业务 key，不污染真实 document prefix | 完成 | 使用 `smoke/rfs-01.txt`，未使用 `source/...` 或 `artifacts/...` |
| bucket 缺失时，runbook 能指导定位为部署前提问题 | 完成 | runbook 已说明 bucket 不存在属于部署或本地初始化前提问题 |
| 本 issue 不实现后端 storage adapter，不修改业务读取语义 | 完成 | 本次只完成对象存储前置验证和记录文档 |

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

## Web Console 删除 `smoke/` 问题

验证过程中 RustFS Web Console 曾持续显示 `smoke/` 文件夹，普通删除后仍无法在页面上移除。

定位结果：

- `ListObjectsV2` 查询 `smoke` prefix 时返回 `KeyCount=0`，说明当前版本视角下没有可见对象。
- 使用 delimiter 查询 bucket 根目录时仍返回 `CommonPrefixes: smoke/`。
- 查询 object versions 后发现 bucket 开启了对象版本化，`smoke/` 下存在历史版本和 delete marker。
- Web Console 删除只创建 delete marker，不会永久删除历史版本，因此页面仍可能聚合出 `smoke/` 前缀。

处理结果：

- 已永久删除 `smoke/` 下所有历史对象版本和 delete marker。
- 涉及对象包括：
  - `smoke/2024.emnlp-main.981-mono.pdf`
  - `smoke/5bc8da9281b1fb4c31fb6f887e952f3b611858603.jpg`
  - `smoke/rfs-01.txt`
  - `smoke/向量数据库介绍.md`
- 删除后再次验证：
  - `ListObjectsV2` bucket 根目录返回 `KeyCount=0`。
  - `ListObjectVersions` 查询 `smoke/` prefix 不再返回 version 或 delete marker。

结论：`smoke/` 删除不掉不是对象存储写入或删除不可用，而是版本化 bucket 下历史版本和 delete marker 导致的 Web Console 显示问题。后续如需要彻底清理测试 prefix，应同时清理对象历史版本和 delete marker。

## 后续

- RFS-02 可以开始实现 `local | s3` 存储模式配置与 S3 Client 装配。
- 应用首期仍不负责自动创建 bucket；`myai-documents` 缺失应继续视为部署或本地初始化问题。
