# RAG 分块设计笔记

## 1. 背景 / 问题

RAG 的效果很大程度上取决于“怎么切文档”。  
切得太大，检索不准；切得太碎，上下文断裂，回答会发虚。

这个项目的 V1 目标不是做最复杂的语义切分，而是做一个：

- 可解释
- 可调试
- 能稳定工作的分块策略

## 2. 这次项目里我是怎么遇到它的

如果只是把整篇文档直接送去向量化：

- 检索粒度太粗
- 很难返回可引用的局部片段
- 也没法在前端调试“问题到底出在解析还是分块”

所以我在 V1 里专门补了 `chunks/preview` 接口和分块预览页，让分块过程变成可见资产。

## 3. 我最后采用了什么方案

当前方案是“结构优先 + 长度兜底”：

- 先按段落结构切分
- 识别 Markdown 标题、中文标题、数字编号标题
- 尽量让一个 chunk 保持语义完整
- 段落过长时，再按窗口切分
- 使用 `chunk=500`、`overlap=100`
- 给 chunk 附带 `sourceHint`，保留标题上下文

这样做的好处是：

- 比纯长度切分更接近自然语义
- 比复杂的语义解析更容易稳定落地
- 能通过分块预览接口直接观察切分结果

## 4. 为什么不用别的方案

### 4.1 为什么不是纯固定长度切分

纯长度切分实现简单，但问题也明显：

- 容易把同一段语义切断
- 无法保留章节上下文
- 调试时很难解释“为什么这段被召回”

### 4.2 为什么不是一开始就上更复杂的语义切分

因为 V1 的重点是闭环和可验证性。  
如果一开始就引入更复杂的文档结构解析，系统会更难调试，也更难解释。

## 5. 这件事面试官可能怎么问

### Q1：为什么需要 overlap？

可以回答：

因为真实文本的语义边界和窗口边界经常不一致。  
适度 overlap 可以降低关键信息刚好被切断导致的召回损失。

### Q2：为什么要做 `chunks/preview`？

可以回答：

RAG 很容易陷入“效果不好但不知道哪一步坏了”。  
分块预览把向量化前的文本切片直接暴露出来，可以快速判断问题出在解析、清洗还是切分。

### Q3：你怎么解释“结构优先 + 长度兜底”？

可以回答：

优先尊重文档已有结构，尽量保留语义完整；  
如果结构片段本身太长，再用固定窗口兜底，保证每个 chunk 可被模型和向量库稳定处理。

## 6. 我该怎么回答

一句话总结：

> 我没有把分块当成黑盒，而是做成了“可见、可调、可解释”的 RAG 中间层，这样系统不仅能跑，还能定位效果问题。

## 7. 相关代码 / 文档入口

- 正式文档：[architecture/ingest/processing-execution.md](../../architecture/ingest/processing-execution.md)
- 分块器：[StructuredFallbackDocumentChunker.java](../../../src/main/java/io/github/spike/myai/ingest/infrastructure/chunking/StructuredFallbackDocumentChunker.java)
- 预览接口：[DocumentIngestController.java](../../../src/main/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestController.java)
- 前端预览页：[IngestChunksPreviewPage.tsx](../../../web/src/features/ingest/pages/IngestChunksPreviewPage.tsx)
