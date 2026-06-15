# QA 子域 — 领域模型

> 检索与回答生成（RAG 管线）

## 概述

QA 子域负责检索增强生成（RAG）管线，将用户问题通过语义检索匹配知识库分块，再由 LLM 生成回答并组装引用。领域模型精简（2 个模型 + 3 个端口），业务编排集中在 `AskQuestionApplicationService`。

**RAG 管线流程**：
```
1.  输入规范化（trim + 默认值）
2.  知识库校验（存在 + ACTIVE）
3.  授权校验（三级授权模型）
3.5 可召回版本范围为空保护（立即返回兜底文案，跳过检索）
4.  版本范围确定（查询用户可召回的文档版本）
5.  语义检索（PGVector 余弦相似度，放大系数 4x）
6.  空结果保护（兜底文案，避免幻觉）
7.  提示词构造（参考片段模板）
8.  LLM 回答生成（DashScope qwen-plus）
9.  引用组装 + 陈旧引用检测
9.5 生成结果空值保护（null/空白 → 使用兜底文案）
```

## 领域模型

### AskableDocumentVersion

问答回答生成端口（Domain Port）。将 document 的 latest projection 与最近一个已 INDEXED 版本放在一起，供 QA 应用层判断某条召回分块是否可用于回答，并生成引用版本提示。

#### 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `String` | 文档资产 ID |
| `latestVersionNumber` | `int` | 当前最新版本号 |
| `askableVersionNumber` | `int` | 当前可问答版本号（最近一个 INDEXED 版本） |
| `sourceFilename` | `String` | 可问答版本对应的来源文件名 |
| `sourceUpdatedAt` | `Instant` | 可问答版本最近更新时间 |

#### 业务方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `isLatestVersion()` | `boolean` | 当 `askableVersionNumber == latestVersionNumber` 时返回 true，表示引用未落后于最新版本 |

#### 验证规则（compact constructor）

| 规则 | 说明 |
|------|------|
| `documentId` | 必填，不可为空白 |
| `latestVersionNumber` | 必须 >= 1 |
| `askableVersionNumber` | 必须 >= 1，且不超过 latestVersionNumber |
| `sourceFilename` | 必填，不可为空白 |
| `sourceUpdatedAt` | 必填，不可为 null |

### RetrievedChunk

向量检索命中的分块快照（领域查询模型）。表示检索层返回的最小必要信息，供应用层进行知识库过滤、引用组装与提示词拼接，不绑定具体向量数据库实现细节。

#### 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `String` | 文档资产 ID |
| `kbId` | `String` | 知识库 ID |
| `chunkIndex` | `int` | 分块序号 |
| `content` | `String` | 分块正文 |
| `sourceVersionNumber` | `Integer` | 分块来源的文档版本号（历史向量未写入该字段时可为 null） |
| `sourceFilename` | `String` | 分块来源文件名（历史向量未写入该字段时可为 null） |
| `sourceUpdatedAt` | `Instant` | 分块来源版本更新时间（历史向量未写入该字段时可为 null） |

**便利构造器**：`RetrievedChunk(documentId, kbId, chunkIndex, content)` — 旧向量数据兼容，版本元数据字段设为 null。

---

## 出站端口（Port）

### ChunkRetrievalPort

问答检索端口。定义问答流程所需的"相似分块召回"能力。应用层仅依赖此抽象，不感知底层是 PGVector、Elasticsearch 还是其他检索引擎。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `similaritySearch` | `List<RetrievedChunk>` | `question, topK` | 执行全库语义检索。返回命中分块列表，顺序由相似度排序策略决定 |
| `similaritySearch` | `List<RetrievedChunk>` | `question, topK, scope` | 在指定可问答文档版本范围内执行语义检索。scope 由应用层在完成授权和版本选择后传入，检索实现应将范围下推到底层向量查询，避免先召回无权内容再裁剪 |

**关键常量**：检索候选下限 20、放大倍率 4x

### AnswerGenerationPort

问答回答生成端口。抽象"根据提示词生成回答"的能力，隔离具体大模型 SDK 与配置差异。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `generateAnswer` | `String` | `prompt` | 根据拼装后的提示词生成回答。实现方可返回空值，调用方需自行兜底 |

### AskableDocumentVersionPort

问答可用文档版本查询端口。应用层通过该端口按文档集合批量查询"当前可问答版本"，避免把版本链 SQL 或持久化结构泄露到问答编排逻辑中。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findAskableVersions` | `Map<String, AskableDocumentVersion>` | `workspaceId, documentIds` | 批量查询文档当前可问答版本。仅返回存在最近 INDEXED 版本的文档 |
| `findAskableVersionsForQuestion` | `List<AskableDocumentVersion>` | `currentUser, kbId` | 查询当前用户在指定知识库内可进入问答召回的文档版本集合。同时应用文档版本状态与授权边界 |

---

## 关联关系

```
AskableDocumentVersion ──(版本事实)──→ ingest_document_versions (status = INDEXED)
RetrievedChunk ──(检索结果)──→ vector_store (via documentId + metadata)

跨子域关联：
AskableDocumentVersion ──(查询)──→ ingest 子域版本链
AskableDocumentVersionPort ──(授权)──→ auth 子域授权模型（三级授权）
similaritySearch(scope) ──(下推)──→ PGVector 向量检索
```

## 设计约束

- **版本隔离**：检索必须限定在用户可召回的文档版本范围内，scope 由应用层在授权完成后传入
- **历史向量兼容**：`RetrievedChunk` 的 `sourceVersionNumber` / `sourceFilename` / `sourceUpdatedAt` 可为 null，兼容旧向量数据未写入版本元数据的情况
- **空值保护**：生成结果为 null/空白时使用兜底文案，避免幻觉
- **陈旧引用检测**：引用组装时检测引用版本是否落后于最新版本（`isLatestVersion()`），通过 `AskStaleReferenceSummaryResult` 向前端暴露陈旧引用信息
- **引用组装细节**：`toReferenceResult()` 合并 chunk + AskableDocumentVersion 信息：版本号、源文件更新时间、是否最新版本、最新版本号、源文件名

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描_
