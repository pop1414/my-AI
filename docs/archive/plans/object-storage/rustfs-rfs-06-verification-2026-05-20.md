# RFS-06 发布文档与历史迁移边界完成概览

## 验证信息

- 验证日期：2026-05-20
- 验证目标：同步 RustFS 发布 runbook、配置字段、bucket 初始化、备份/回滚边界和历史迁移后续入口。
- Runbook：`docs/runbooks/operations/rustfs-object-storage.md`
- 历史迁移入口：`docs/runbooks/plans/object-storage/rustfs-history-migration-plan.md`

## 完成结论

RFS-06 已完成。文档已明确首期 `s3` 模式只覆盖新上传 source 与新生成 artifacts，不迁移既有 `data/ingest`，RustFS 不可用时不 fallback 到本地文件系统，回滚仅通过配置切回 `local`，且 PostgreSQL 备份不能替代 RustFS source/artifacts 备份。

## 改动范围

| 文件 | 说明 |
| --- | --- |
| `docs/runbooks/operations/rustfs-object-storage.md` | 对齐当前配置字段，补充 Docker Compose、bucket 初始化、发布边界、回滚和备份说明 |
| `docs/runbooks/plans/object-storage/rustfs-history-migration-plan.md` | 新增历史 `data/ingest` 迁移后续 plan/spec 入口，不实现迁移 |
| `docs/runbooks/plans/object-storage/rustfs-issues-draft-2026-05-20.md` | 回填 RFS-06 acceptance criteria 完成状态 |
| `docs/runbooks/plans/object-storage/rustfs-rfs-06-verification-2026-05-20.md` | 新增 RFS-06 完成概览 |

## Acceptance Criteria 对照

| Acceptance criteria | 状态 | 说明 |
| --- | --- | --- |
| RustFS runbook 与最终配置字段一致 | 完成 | Runbook 已覆盖 `type`、`root-dir`、`s3.*` 与 `artifacts.*` 配置 |
| RustFS runbook 明确 bucket 初始化、endpoint、region、path-style access 和凭证配置 | 完成 | Runbook 已补充本地默认、配置说明、PowerShell 环境变量和 bucket 初始化要求 |
| RustFS runbook 明确首期不迁移历史 `data/ingest` | 完成 | 发布边界、常见问题和历史迁移入口均已说明 |
| RustFS runbook 明确 RustFS 不可用时不 fallback 到本地文件系统 | 完成 | 发布边界和常见问题已说明不允许 fallback |
| RustFS runbook 明确回滚只通过配置切回 `local`，不会自动复制 RustFS 对象回本地 | 完成 | 回滚章节已说明只影响新写入，RustFS 对象不会自动复制回本地 |
| 文档明确 PostgreSQL 备份不能替代 RustFS source/artifacts 备份 | 完成 | 发布边界和备份章节已说明数据库与对象存储需要分别备份 |
| 如需要历史迁移，仅创建后续 plan/spec 入口，不在本 issue 中实现迁移 | 完成 | 新增 `rustfs-history-migration-plan.md` 作为后续入口，明确非目标 |

## 当前边界

- RFS-06 不修改 Java 代码。
- RFS-06 不实现历史迁移。
- RFS-06 不新增双读 fallback 或自动同步任务。
- RFS-06 不改变正文读取 API、权限规则、响应字段或错误码。
