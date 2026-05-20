# PGVector 向量写入设计笔记

## 1. 背景 / 问题

RAG 项目里不仅要“把文本变成向量”，还要解决：

- 这批向量和哪个文档相关
- 重处理后如何替换旧向量
- 删除文档时如何清理向量
- 检索回来后如何把结果还原成业务上的引用

如果向量层只存文本而不带业务元数据，后续很多能力都会很难做。

## 2. 这次项目里我是怎么遇到它的

在做完上传、解析、分块之后，我需要一个能落地的向量存储方案。  
如果只是简单调用向量库 add 文本，项目很快会遇到几个现实问题：

- 重复写入怎么避免
- 重处理后旧版本怎么清理
- 问答结果怎么带回 `documentId` 和 `chunkIndex`

## 3. 我最后采用了什么方案

当前方案是：

- 使用 PostgreSQL + PGVector 作为 V1 向量存储
- 每个 chunk 写入时附带元数据：
  - `documentId`
  - `kbId`
  - `chunkIndex`
  - `sourceFile`
  - `contentHash`
  - `splitVersion`
  - `sourceHint`
- chunk 主键使用“确定性 UUID”
  - 由 `documentId + chunkIndex + splitVersion` 生成
- 支持按：
  - `documentId + splitVersion` 删除旧版本
  - `documentId` 删除全部版本

## 4. 为什么不用别的方案

### 4.1 为什么不是简单本地向量缓存

本地缓存更轻，但不利于：

- 统一查询
- 数据持久化
- 删除和版本管理

### 4.2 为什么要存业务元数据

因为问答层不只需要“最相似文本”，还需要：

- 按知识库过滤
- 返回引用信息
- 支持删除和重处理

没有这些元数据，向量库就只是一个难以管理的黑盒。

### 4.3 为什么要做确定性 ID

因为重试和重处理场景下，系统可能多次生成同一批 chunk。  
如果 ID 每次随机，就很难保证幂等，也不方便覆盖旧数据。

## 5. 这件事面试官可能怎么问

### Q1：为什么用 PGVector，而不是单独的向量数据库？

可以回答：

V1 更重视开发门槛和系统闭环。  
PostgreSQL + PGVector 在一个数据底座里同时解决结构化数据和向量数据，足够支撑基线版本。

### Q2：为什么还要保留 `splitVersion`？

可以回答：

因为文档重处理后，新旧 chunk 并不一定完全一样。  
`splitVersion` 让系统可以区分不同批次的分块结果，支持平滑替换和定向清理。

### Q3：问答引用为什么能带回 `documentId/chunkIndex`？

可以回答：

因为这些字段在写入向量库时就作为元数据保存了，检索适配器只是在召回后把这些元数据重新映射成领域对象和返回结构。

## 6. 我该怎么回答

一句话总结：

> 我把向量层设计成“可追踪、可删除、可重处理”的业务化存储，而不是只做一个能跑的 embedding demo。

## 7. 相关代码 / 文档入口

- 正式文档：[architecture/README.md](../../architecture/README.md)
- V1 基线 ADR：[ADR-0003-v1-dashscope-pgvector.md](../../adr/ADR-0003-v1-dashscope-pgvector.md)
- 向量写入实现：[PgVectorDocumentVectorIndexer.java](../../../src/main/java/io/github/spike/myai/ingest/infrastructure/vector/PgVectorDocumentVectorIndexer.java)
- 检索适配器：[PgVectorChunkRetrievalAdapter.java](../../../src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java)
