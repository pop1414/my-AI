# Investigation: cleaned.md 渲染链路

## Hand-off Brief

1. **What happened.** 最初把管理端阅读页中的图片缺失归因为浏览器无法直连外部图片，因此额外实现了一条后端外部图片代理链路。
2. **What the evidence showed.** 实际根因不是浏览器取图失败，而是阅读页展示的正文并不是面向阅读保真的产物；同时管理端页面还在使用正则拼接 HTML 的简化渲染方式。
3. **Final outcome.** 已确认并落地两项修复：后端按 `reader.md / cleaned.md` 双轨输出正文产物；前端阅读页切换到真正的 Markdown 渲染方案。误判产生的外部图片代理链路已删除。

## Case Info

| Field | Value |
| --- | --- |
| Date opened | 2026-06-18 |
| Status | Concluded |
| Confidence | High |
| Scope | ingest / web |

## Problem Statement

需要确认管理端“阅读正文”页面为什么会出现渲染粗糙、图片缺失，以及这些问题分别属于后端正文产物链路还是前端渲染链路。

## Confirmed Findings

### Finding 1: `cleaned.md` 本来就不是面向阅读保真的唯一正文

后端原先把同一份正文同时用于阅读展示和 RAG 分块，导致“阅读保真”和“RAG 清洗”两个目标互相冲突。  
后续已经通过 `reader.md / cleaned.md` 双轨方案拆开：

- `reader.md` 面向阅读展示
- `cleaned.md` 面向分块与向量化

### Finding 2: 管理端阅读页当时没有使用真正的 Markdown 渲染器

管理端页面最初通过正则替换再配合 `dangerouslySetInnerHTML` 拼接 HTML，语法覆盖极不完整，二级标题、列表、表格、代码块等结构都可能被误渲染或弱渲染。

### Finding 3: “外部图片代理”是误诊后的补丁，不是根因修复

当时为了绕过第三方图床访问问题，引入了后端 `/api/v1/documents/external-images` 代理链路。  
在确认正文源头问题后，这条链路已经失去必要性，并已从后端与前端中移除。

### Finding 4: 阅读页最终需要的是富 Markdown 渲染，而不是继续堆补丁

最终保留的方案是：

- `react-markdown + remark-gfm`
- HTML 片段安全清洗
- 较完整的 Markdown/GFM 样式
- KaTeX 公式渲染
- 图片加载失败时的前端兜底展示

## Conclusion

本次问题实际由两层组成：

1. 后端正文产物链路没有区分“阅读正文”和“RAG 正文”。
2. 前端管理端阅读页没有使用真正的 Markdown 渲染器。

已经采取的最终修复：

- 后端保留 `reader.md` 供阅读，`cleaned.md` 供 RAG。
- 前端管理端阅读页升级为富 Markdown 渲染。
- 误判产生的外部图片代理链路已删除。

## Recommended Future Triage

以后再遇到“阅读页显示不对”，建议按下面顺序排查：

1. 先确认当前页面读的是 `reader.md` 还是 `cleaned.md`
2. 再确认正文内容本身是否已经在后端被清洗或裁剪
3. 最后再看前端渲染器、样式或资源加载问题
