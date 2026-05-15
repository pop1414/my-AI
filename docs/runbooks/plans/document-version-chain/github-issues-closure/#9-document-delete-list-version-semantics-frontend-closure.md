# 删除确认与列表页版本语义前端收口说明

## 1. 收口范围

本文用于记录 GitHub issue #9《删除确认与列表页版本语义前端》的完成情况。

本次收口覆盖前端删除确认、列表页 latest version 语义展示、详情页删除后返回列表上下文、删除成功稳定结果提示、可访问性收口和对应前端 E2E 测试；不包含后端删除语义或 QA 引用版本展示。

## 2. 实现落点

- 删除确认 Modal：`web/src/features/ingest/pages/DeleteDocumentConfirmModal.tsx`
- 文档列表页：`web/src/features/ingest/pages/IngestListPage.tsx`
- 文档详情页：`web/src/features/ingest/pages/IngestDocumentDetailPage.tsx`
- 旧删除入口页：`web/src/features/ingest/pages/IngestDeletePage.tsx`
- Ingest API client：`web/src/shared/api/ingestApi.ts`
- 前端 E2E 覆盖：`web/e2e/document-version-history.spec.ts`

## 3. 验收对照

- 删除确认已改为结构化 Modal，用户必须输入完整 `documentId` 才能启用“确认删除整个 document”按钮。
- 删除确认文案明确提示：删除后同内容重新上传会生成新的 `documentId`，新文档不会继承旧文档级授权。
- 列表页删除成功后会关闭 Modal、刷新文档列表，并在当前列表上下文展示稳定结果提示。
- 详情页新增删除入口，删除成功后返回文档列表，并通过 `returnTo` 尽量保留原筛选和分页上下文。
- 删除成功结果提示区展示旧 `documentId`，并提供重新上传新文档的后续入口。
- 文档列表展示 latest version 语义字段，包括当前最新文件名、最新版本号、最新版本来源和当前最新状态。
- 旧删除页复用同一个结构化确认 Modal，避免保留 Popconfirm 的弱确认路径。
- 前端测试覆盖列表页删除确认、删除后列表即时更新、详情页删除后返回列表上下文和结果提示。

## 4. 可访问性与界面收口

- 删除确认输入框补充 `aria-label`、`autoComplete="off"` 与 `spellCheck={false}`，避免 documentId 被拼写检查干扰。
- 旧删除页 documentId 输入框同步补充可访问名称和输入属性。
- 列表页跳转类操作改为真实 `Link`，支持中键打开、新标签页和浏览器原生链接行为。
- 列表页筛选控件补充 `aria-label`，避免仅依赖 placeholder 作为可访问名称。
- 删除成功结果提示区补充 `aria-live="polite"` 与 `aria-atomic="true"`，确保异步结果能被辅助技术感知。
- 通过 `web-design-guidelines` 对 #9 相关交互完成审阅，并修复审阅发现的问题。

## 5. 已知后续项

- 全量 `npm.cmd run lint` 当前仍受仓库既有 lint 问题阻断，失败位置不在本次 #9 修改范围内。
- 列表页操作 Link 当前使用内联样式对齐 Ant Design 小按钮外观；后续如果继续扩展列表操作，可抽出共享样式类以减少重复。

## 6. 测试结果

执行位置：`web/`

```text
npm.cmd run build
```

结果：通过。`tsc -b && vite build` 成功完成。

```text
npm.cmd run test:e2e -- document-version-history.spec.ts
```

结果：通过。`document-version-history.spec.ts` 共 10 个用例全部通过。

```text
npm.cmd run lint
```

结果：未通过。失败点来自既有页面与共享组件的 React Hooks / Fast Refresh 规则问题，不是本次 #9 修改引入。

## 7. 审阅建议

建议按以下顺序审阅：

1. 先看 `DeleteDocumentConfirmModal.tsx`，确认删除动作必须输入完整 `documentId`，且高风险后果文案完整覆盖身份断裂与授权不继承。
2. 再看 `IngestListPage.tsx`，确认筛选与分页写入 URL、列表 latest version 字段展示、删除后刷新和结果提示。
3. 检查 `IngestDocumentDetailPage.tsx`，确认 `returnTo` 只在入口显式携带时保留，删除成功后返回原列表上下文。
4. 检查 `IngestDeletePage.tsx`，确认旧删除页不再保留 Popconfirm 弱确认路径。
5. 最后看 `document-version-history.spec.ts`，确认删除确认、列表刷新、详情返回列表和稳定结果提示均有端到端覆盖。
