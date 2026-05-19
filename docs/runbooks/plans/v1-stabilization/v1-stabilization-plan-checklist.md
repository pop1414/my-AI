# V1 稳定化推进计划清单

状态：计划清单
日期：2026-05-19
来源：`docs/runbooks/plans/v1-stabilization/v1-stabilization-audit.md`

本清单用于把稳定化审计结论拆成可逐步推进的执行顺序。推进原则是：先确认基线，再补文档和测试边界，最后处理高风险后端代码；前端改动排在后端稳定后执行。

## 1. 阶段 0：确认稳定化基线

- [x] 确认工作树状态：

```powershell
git status --short
```

- [x] 执行后端测试：

```powershell
.\mvnw.cmd -q test
```

- [x] 执行前端构建与 E2E：

```powershell
cd web
npm.cmd run build
npm.cmd run test:e2e
```

- [x] 记录当前失败项；如果已有失败，先修复或归档失败原因，再进入文档同步和重构。

验收标准：

- 当前分支的后端测试、前端构建和 E2E 状态明确。
- 已知失败项有记录，不把历史失败误判为稳定化改动引入。

## 2. 阶段 1：补齐 P0 恢复性文档

- [x] 新增或补充 `ingest` 文档资产副作用恢复 runbook。
- [x] 定义 `orphan source`、`missing source`、`orphan artifact`、`orphan vector`、`DELETING` 卡住等状态的识别方式。
- [x] 明确每类状态的本地检查命令、人工恢复步骤和风险提示。
- [x] 明确哪些恢复操作需要停止 worker 或避免并发上传、删除、回退请求。
- [x] 在 `docs/runbooks/` 或相关导航中补充入口。

执行产物：

- [../../troubleshooting/ingest-document-asset-recovery.md](../../troubleshooting/ingest-document-asset-recovery.md)
- [../../troubleshooting/README.md](../../troubleshooting/README.md)

验收标准：

- 不阅读源码也能根据 runbook 完成半成功状态排查。
- runbook 明确区分“可直接修复”“需要停止处理流程后修复”“需要先备份后修复”的场景。

建议提交分组：

- 文件：`docs/runbooks/**`
- Commit message：

```text
docs(ingest): 补充文档资产副作用恢复手册

- 定义源文件、处理产物、向量数据与数据库状态不一致时的排查入口
- 补充 orphan source、missing source、orphan vector 与 DELETING 残留状态的恢复步骤
- 明确人工恢复前需要停止并发处理或备份数据的边界
```

## 3. 阶段 2：同步产品范围和路线图事实

- [ ] 更新 `docs/01-product-scope.md`，说明原 V1 范围与当前已落地权限基线的差异。
- [ ] 更新 `docs/02-roadmap.md`，把权限体系拆成“已落地基线”和“后续增强”。
- [ ] 更新 `README.md`，补齐账号治理、成员管理、知识库授权、文档授权和审计 API 摘要。
- [ ] 统一 README 中的“截至日期”，避免同一入口出现多个事实时间。

验收标准：

- 文档不再把已经实现的 `auth/governance` 能力描述为未规划或未实现。
- 读者能区分“历史 V1 约束”和“当前工程基线”。

建议提交分组：

- 文件：`docs/01-product-scope.md`、`docs/02-roadmap.md`、`README.md`
- Commit message：

```text
docs(scope): 同步 V1 范围与权限治理事实

- 标注原 V1 单用户范围与当前权限基线之间的差异
- 将路线图中的权限体系状态拆分为已落地能力和后续增强
- 补齐 README 中账号治理、授权和审计能力摘要
```

## 4. 阶段 3：同步架构当前事实

- [ ] 更新 `docs/03-architecture.md` 的目标架构章节。
- [ ] 将“当前实现事实”和“未来目标蓝图”拆成独立小节。
- [ ] 当前事实明确写为 PostgreSQL/PGVector、本地存储、Session auth、无 SSE。
- [ ] 未来蓝图再说明 MySQL、MinIO、Tenant、SSE 等演进方向。
- [ ] 检查架构文档是否仍把未实现能力写成当前能力。

验收标准：

- 读者不会误以为当前已经实现 Tenant、SSE、MinIO 或 MySQL 迁移。
- 当前实现事实能和 OpenAPI、README、代码 module 现状互相印证。

建议提交分组：

- 文件：`docs/03-architecture.md`
- Commit message：

```text
docs(architecture): 区分当前实现事实与未来目标蓝图

- 将 PostgreSQL、PGVector、本地存储和 Session 认证标注为当前实现
- 将 Tenant、SSE、MinIO 和 MySQL 迁移保留为未来演进方向
- 调整目标架构章节，降低当前能力和蓝图能力混读风险
```

## 5. 阶段 4：补后端 characterization tests

- [ ] 为 latest projection 状态推进补统一 contract test 或等价 characterization tests。
- [ ] 覆盖上传新版本时源文件保存失败的状态结果。
- [ ] 覆盖版本回退时来源文件缺失的状态结果。
- [ ] 覆盖删除时源文件清理成功但向量删除失败后的状态回滚。
- [ ] 覆盖 `DELETING` 残留状态的人工恢复前置条件。

验收标准：

- 测试只锁住当前行为，不主动改变业务流程。
- 半成功状态、状态回滚、审计记录和错误分类都有明确断言。
- `.\mvnw.cmd -q test` 通过。

建议提交分组：

- 文件：`src/test/java/**`
- Commit message：

```text
test(ingest): 补充文档副作用半成功场景回归测试

- 覆盖上传新版本源文件保存失败后的状态表现
- 覆盖回退来源文件缺失和删除清理失败场景
- 锁定半成功状态下的回滚、审计和错误分类语义
```

## 6. 阶段 5：收敛 latest projection 状态推进

- [ ] 新增 `LatestDocumentVersionProjection` 或等价 persistence module。
- [ ] 将 `markDeleting` 的 latest projection 维护迁移到统一入口。
- [ ] 将 `markDeleted` 的 latest projection 维护迁移到统一入口。
- [ ] 将 `rollbackDeleting` 的 latest projection 维护迁移到统一入口。
- [ ] 将 `requestReprocess` 的 latest projection 维护迁移到统一入口。
- [ ] 保留现有 CAS 冲突语义、错误码和数据库函数行为。
- [ ] 检查新增或迁移路径中是否仍手写 `ingest_documents` 与 `ingest_document_versions` 镜像状态 SQL。

验收标准：

- 删除、重处理、上传新版本、版本回退测试全部通过。
- CAS 冲突错误码保持不变。
- latest projection 的状态推进集中在一个明确 persistence seam。

建议验证：

```powershell
.\mvnw.cmd -q test
```

建议提交分组：

- 文件：`src/main/java/io/github/spike/myai/ingest/**`、`src/test/java/io/github/spike/myai/ingest/**`
- Commit message：

```text
refactor(ingest): 收敛 latest projection 状态推进入口

- 新增统一的 latest projection 维护入口，集中处理主表与版本表状态同步
- 迁移删除、重处理和状态回滚路径中的双表镜像更新逻辑
- 保持现有 CAS 冲突、错误码和版本治理语义不变
```

## 7. 阶段 6：制定 QA 向量元数据兼容清理计划

- [ ] 明确 `documentVersionNumber` 是新向量元数据事实源。
- [ ] 记录历史 `splitVersion=v1` 的兼容原因。
- [ ] 定义停止依赖历史 `splitVersion=v1` 的前置条件。
- [ ] 补充 QA 检索、版本化引用和 stale 汇总的回归测试清单。
- [ ] 暂不删除兼容逻辑，避免影响已有历史向量。

验收标准：

- 后续清理历史向量兼容逻辑时，有明确迁移前置条件。
- QA 版本引用和 stale 汇总语义不变。

建议提交分组：

- 文件：`docs/runbooks/plans/**`
- Commit message：

```text
docs(qa): 制定向量元数据兼容清理计划

- 明确 documentVersionNumber 作为新向量元数据事实源
- 记录历史 splitVersion 兼容逻辑的退出条件
- 补充 QA 检索、版本引用和 stale 汇总的回归检查清单
```

## 8. 阶段 7：修正认证链路说明与 CSRF 升级边界

- [ ] 修正 `SecurityConfig` 中容易误导的“占位实现”说明。
- [ ] 文档说明当前认证链路是自定义登录服务加 Session 维护认证态。
- [ ] 说明固定 Header CSRF 是阶段性安全基线。
- [ ] 将动态 CSRF token 拆成独立后续项，不与当前文档同步混做。
- [ ] 回归 `AuthSecurityBaselineTest` 与 `login.spec.ts`。

验收标准：

- 后续维护者不会误以为当前认证仍未接入真实账号数据源。
- 动态 CSRF 的升级范围和前端影响边界清楚。

建议提交分组：

- 文件：`src/main/java/io/github/spike/myai/auth/**`、`README.md` 或相关 auth 文档
- Commit message：

```text
docs(auth): 修正认证链路说明与 CSRF 升级边界

- 说明当前登录认证通过自定义登录服务和 Session 落地
- 将固定 Header CSRF 标注为阶段性安全基线
- 拆分动态 CSRF token 为后续独立升级项
```

## 9. 阶段 8：收敛 REST 错误码迁移边界

- [ ] 为 ingest/qa 的用户可见冲突、权限和状态错误定义最小业务错误码清单。
- [ ] 保留纯参数错误使用 `BAD_REQUEST` 的路径。
- [ ] 分批替换控制器中直接抛出的 `ResponseStatusException`。
- [ ] 同步前端错误展示和测试断言。
- [ ] 每迁移一类错误码，就单独回归对应 controller tests 和前端提示。

验收标准：

- 前端可依赖稳定业务错误码展示用户可见错误。
- 错误码迁移不破坏已有 API 契约和 E2E。

建议提交分组：

- 文件：`src/main/java/io/github/spike/myai/shared/**`、`src/main/java/io/github/spike/myai/ingest/**`、`src/main/java/io/github/spike/myai/qa/**`、相关测试
- Commit message：

```text
refactor(api): 收敛 ingest 与 qa 用户可见错误码

- 为冲突、权限和状态类错误补充稳定业务错误码
- 分批替换控制器中直接暴露的 HTTP 状态异常
- 同步后端测试断言，保持前端错误展示语义稳定
```

## 10. 阶段 9：最后处理前端文档和轻量测试基线

- [ ] 更新 `web/README.md`，同步系统管理、成员管理、授权、审计、成员阅读页和问答基线阅读页能力。
- [ ] 明确当前 Playwright E2E 覆盖范围。
- [ ] 评估是否引入 Vitest 或等价 module-level 测试工具。
- [ ] 如果引入，优先覆盖 `shared/api` 的 zod schema、错误解析和 `shared/auth` 的能力位路由。
- [ ] 不用轻量测试替代现有 Playwright E2E。

验收标准：

- 前端 README 与当前控制台能力一致。
- 是否引入 module-level tests 有明确结论。
- 如引入测试框架，首批测试覆盖 API 错误解析、权限能力位和默认落点。

建议提交分组：

- 文件：`web/README.md`、`web/src/shared/**`、`web/e2e/**`、前端测试配置文件
- Commit message：

```text
test(frontend): 评估前端 API 与权限路由轻量测试基线

- 同步前端 README 中系统管理、授权和审计能力说明
- 明确现有 Playwright E2E 覆盖范围
- 评估并建立 shared/api 与 shared/auth 的轻量测试入口
```

## 11. 推进审阅方式

每完成一个阶段，按以下顺序审阅：

1. 先看本阶段是否只修改了计划内文件。
2. 再看文档事实是否与当前代码、OpenAPI 和测试一致。
3. 如果是测试阶段，确认测试锁住的是当前行为，而不是提前改变业务语义。
4. 如果是代码重构阶段，优先审查错误码、状态流转、CAS 冲突、审计记录和回滚路径。
5. 最后执行对应验证命令，并把失败项区分为历史失败、环境失败或本阶段新失败。

建议优先审阅的高风险点：

- latest projection 是否仍存在分散的双表镜像更新。
- 删除、回退、重处理是否改变了原有状态流转。
- 文件系统、处理产物、向量库和数据库之间的半成功状态是否可解释、可恢复。
- 权限和认证文档是否把当前真实实现描述成占位实现。
- 前端是否依赖不稳定的 HTTP 状态名而不是业务错误码。
