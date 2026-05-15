# 文档详情页版本历史前端只读视图收口说明

## 1. 收口范围

本文用于记录 GitHub issue #3《文档详情页版本历史前端只读视图》的完成情况。

本次收口只覆盖文档详情页的版本历史只读查看能力，不包含上传新版本、版本回退、删除、重处理等治理动作接入。

## 2. 实现落点

- 前端详情页入口：`web/src/features/ingest/pages/IngestDocumentDetailPage.tsx`
- 前端详情页样式：`web/src/features/ingest/pages/IngestDocumentDetailPage.css`
- 版本历史 API client：`web/src/shared/api/ingestApi.ts`
- 路由配置：`web/src/app/AppRoutes.tsx`
- 前端 E2E 覆盖：`web/e2e/document-version-history.spec.ts`

详情页通过 `GET /api/v1/documents/{documentId}/versions` 获取版本历史，不基于 mock 数据或临时复用 status/list 接口推进正式视图。

## 3. 验收对照

- 已展示版本历史列表，列表项包含版本号、来源文件名、上传人、更新时间、状态、最新版本标记、当前查看标记与当前问答基线标记。
- 当后端返回 `403` 时，页面展示“旧版本视图不可见”，不渲染版本历史列表，满足没有目标 `document` 管理权限时不暴露旧版本视图的边界。
- 通过 `/ingest/documents/{documentId}?version=N` 进入历史版本查看态时，页面展示“正在查看历史版本 vN”提示，并提供“返回最新版本”入口。
- 历史版本查看态只改变当前页面的 `version` query 与查看上下文，不写入后端、不触发版本治理动作，也不改变当前问答基线。
- 差异摘要区按照 #2 的交互确认放在主概览区前，历史版本默认与系统最新版本对比；当前阶段只展示结构化元数据差异，不做正文 diff。
- 前端测试已覆盖版本历史只读展示、历史版本查看态、返回最新版本入口和无管理权限不可见分支。

## 4. 测试结果

执行位置：`web/`

```text
npm.cmd run build
```

结果：通过。`tsc -b && vite build` 成功完成。

```text
PLAYWRIGHT_BASE_URL=http://127.0.0.1:5173 npm.cmd run test:e2e -- document-version-history.spec.ts
```

结果：通过。`document-version-history.spec.ts` 共 3 个用例全部通过。

说明：首次 E2E 运行时本地 Vite 服务未成功保持运行，导致 `ERR_CONNECTION_REFUSED`；随后使用同一 PowerShell 作业内启动 Vite 并重跑，页面用例全部通过。该失败不对应页面行为断言失败。

## 5. 审阅建议

建议按以下顺序审阅：

1. 先看 `web/src/shared/api/ingestApi.ts`，确认前端消费的 `DocumentVersionHistoryResponse` 与 #20 后端契约一致。
2. 再看 `web/src/features/ingest/pages/IngestDocumentDetailPage.tsx`，重点核对 `latestVersion`、`askableVersion`、`viewingVersion` 三套事实是否分开表达。
3. 检查无权限分支是否只依赖后端 `403`，避免前端自行推断管理权限造成绕过。
4. 检查历史版本查看态是否只通过 query 表达，不触发上传新版本、版本回退或问答基线变更。
5. 最后看 `web/e2e/document-version-history.spec.ts`，确认测试覆盖了列表展示、历史查看、返回最新版本和无权限不可见分支。

