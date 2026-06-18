---
title: '管理端 reader.md / cleaned.md 阅读页富 Markdown 渲染'
type: 'bugfix'
created: '2026-06-18T00:00:00+08:00'
status: 'done'
context:
  - '{project-root}/docs/project-context.md'
baseline_commit: '1e71b71f8b604eee50c607adaebb9e6958ac7f8a'
---

## Intent

**Problem:** 管理端文档阅读页最初用正则加 `dangerouslySetInnerHTML` 渲染正文，语法覆盖不足；同时还一度通过后端代理去兜底外部图片问题，偏离了真正根因。  
**Goal:** 保留富 Markdown 阅读体验，确保页面能够稳定展示 `reader.md / cleaned.md` 对应的 Markdown、GFM、HTML 片段与公式内容，同时移除误判产生的外部图片代理依赖。

## Final Approach

1. 管理端阅读页切换为真正的 Markdown 渲染方案：
   - `react-markdown`
   - `remark-gfm`
2. 对正文中的 HTML 片段做安全清洗后再渲染。
3. 保留 KaTeX 自动公式渲染。
4. 补齐阅读页的 Markdown/GFM 样式。
5. 不再依赖后端 `/external-images` 图片代理；图片直接由前端按原始地址加载，失败时走页面内 fallback。

## Boundaries

**Always**

- 不改正文读取 API 契约
- 不改页面整体布局
- 保留单栏与双栏对比模式
- 保留富 Markdown 渲染能力

**Never**

- 不再恢复正则拼接 HTML 的旧方案
- 不再引入专门的后端外部图片代理
- 不把本次阅读页修复扩展成新的设计系统改造

## Code Map

- `web/src/features/ingest/pages/IngestDocumentVersionReadPage.tsx`
  管理端阅读页主渲染链路
- `web/src/features/ingest/pages/IngestDocumentVersionReadPage.css`
  阅读页 Markdown/GFM 样式
- `web/index.html`
  KaTeX 资源入口
- `web/src/shared/api/request.ts`
  通用 API URL 构建 helper

## Execution

- [x] 用 `react-markdown + remark-gfm` 替换管理端阅读页旧的正则渲染
- [x] 增加 HTML 片段安全清洗与分段渲染
- [x] 增加图片失败兜底展示
- [x] 增加 Markdown/GFM 基础样式
- [x] 增加 KaTeX 自动公式渲染
- [x] 移除前端对 `/api/v1/documents/external-images` 的依赖

## Acceptance Criteria

- Given 管理端打开带标题、列表、表格、代码块的正文  
  When 页面渲染内容  
  Then Markdown/GFM 结构应按语义正确展示

- Given 管理端进入版本对比模式  
  When 左右 Pane 分别加载不同版本正文  
  Then 两侧都应使用同一套渲染链路并保持布局稳定

- Given 正文中包含图片或公式  
  When 页面渲染内容  
  Then 图片正常加载或优雅 fallback，公式可被 KaTeX 正确处理

## Verification

**Commands**

- `npm run build`

**Result**

- 已通过 `npm run build`

## Outcome

本次实现的最终结论是：

- 富 Markdown 阅读体验保留
- 后端外部图片代理链路不再需要
- 管理端阅读页现在和正文双轨方案保持一致：阅读问题先看 `reader.md`，而不是继续在 `cleaned.md` 渲染层叠补丁
