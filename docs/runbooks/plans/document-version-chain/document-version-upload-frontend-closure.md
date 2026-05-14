# 上传新版本前端交互与结果提示收口说明

## 1. 收口范围

本文用于记录 GitHub issue #5《上传新版本前端交互与结果提示》的完成情况。

本次收口覆盖文档详情页中的上传新版本入口、提交流程、结果提示与前端回归测试；不包含版本回退、删除确认、问答引用版本提示等后续治理流程。

## 2. 实现落点

- 前端详情页入口：`web/src/features/ingest/pages/IngestDocumentDetailPage.tsx`
- 前端详情页样式：`web/src/features/ingest/pages/IngestDocumentDetailPage.css`
- 版本上传 API client：`web/src/shared/api/ingestApi.ts`
- 前端 E2E 覆盖：`web/e2e/document-version-history.spec.ts`

详情页通过 `POST /api/v1/documents/{documentId}/versions` 提交新版本，上传时只携带当前 `documentId`、文件和 `expectedLatestVersionNumber`，不提供 knowledge base 切换项。

## 3. 验收对照

- 已按当前最新版本状态控制上传入口显隐：仅当详情页处于最新版本视图，且最新版本状态为 `INDEXED` 或 `FAILED` 时展示入口。
- 上传新版本 Modal 锁定当前 document 所属 knowledge base，不允许在提交流程中切换 knowledge base。
- 提交时携带 `expectedLatestVersionNumber`，用于后端乐观并发校验。
- 创建新版本成功后，页面清除历史版本 query，回到当前最新版本视图，并刷新版本历史。
- 稳定结果提示区展示 `documentId`、`latestVersionNumber`、`previousVersionNumber`、`status`、`askableVersionNumber` 等关键事实。
- 结果提示区提供“查看版本历史”“关闭提示”，并在 `canAskNow = true` 时提供“去问答”入口。
- 命中同内容复用时，页面明确提示“未创建新版本”，并停留在原最新版本。
- 前端测试覆盖入口显示规则、创建新版本结果、同内容复用结果和请求体不携带 `kbId`。

## 4. 可访问性与界面收口

- 上传结果提示区已补充 `aria-live="polite"` 与 `aria-atomic="true"`，保证异步结果能被辅助技术感知。
- 结果提示区根据 `versionCreated` 区分 success/info 视觉语义；同内容复用使用信息态，不混用成功边框。
- 结果事实区使用自适应网格，避免长字段名在宽屏下被固定 5 列布局压碎。
- 文档详情页标题层级调整为 `h2 -> h3 -> h4`，避免跳过标题层级。
- header 中的长 `documentId` 支持收缩、换行、省略、展开和复制，避免挤压右侧操作区。
- 同屏重复的“上传新版本”入口已做层级区分：header 保留主按钮，详情卡内入口降级为次级按钮。
- “正文阅读待接入”已从 disabled button 改为普通状态说明，避免依赖不可聚焦控件的 `title` 说明原因。

## 5. 已知后续项

- 上传 Modal 在已选择文件后点击取消或关闭仍会直接清空文件。该项不属于 #5 验收阻断，后续 UI 收口时可追加“已选择文件”状态与取消确认。
- 全量 `npm.cmd run lint` 当前仍受仓库既有 lint 问题阻断，失败位置不在本次 #5 改动范围内。

## 6. 测试结果

执行位置：`web/`

```text
npm.cmd run build
```

结果：通过。`tsc -b && vite build` 成功完成。

```text
npm.cmd run test:e2e -- document-version-history.spec.ts
```

结果：通过。`document-version-history.spec.ts` 共 6 个用例全部通过。

```text
npm.cmd run lint
```

结果：未通过。失败点来自既有页面与共享组件的 React Hooks / Fast Refresh 规则问题，不是本次 #5 修改引入。

## 7. 审阅建议

建议按以下顺序审阅：

1. 先看 `web/src/shared/api/ingestApi.ts`，确认 `uploadNewDocumentVersion` 只提交 `file` 与 `expectedLatestVersionNumber`，不提交 `kbId`。
2. 再看 `web/src/features/ingest/pages/IngestDocumentDetailPage.tsx`，重点核对上传入口显隐、Modal 提交、成功后刷新历史与结果提示区。
3. 检查 `web/src/features/ingest/pages/IngestDocumentDetailPage.css`，确认结果提示、header 长文本和事实网格在窄宽下不挤压。
4. 最后看 `web/e2e/document-version-history.spec.ts`，确认入口规则、创建新版本、同内容复用和请求体约束都已覆盖。
