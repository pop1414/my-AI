# RAG 节点候选数据契约（未来草案）

**版本**：0.2-draft

**定位**：面向后续父子分块与 richer node model 的前瞻草案

**重要说明**

- 本文档不是当前 `ingest-cleaning` 轮次的执行契约
- 本文档不是当前 `processingMetadata` 的定义
- 本文档不是当前 `qa.ask` response 的定义
- 本文档不是当前 `vector metadata shape` 的定义
- 当前轮次不以落地本文档为目标

---

## 1. 使用边界

本文档只回答一个问题：

当后续正式推进父子分块、结构化节点与 richer retrieval/reference 能力时，候选节点数据模型大致应该长什么样。

它当前**不承诺**以下事情：

- 何时进入实现
- 何时进入 API 契约
- 何时进入前端引用展示
- 何时进入向量元数据稳定字段集

---

## 2. 设计目标

未来节点模型需要同时满足：

- 能表达文本、表格、代码、图片等不同节点类型
- 能表达父子分块关系
- 能区分向量索引内容与展示内容
- 能兼容标题路径、来源文件、语言、质量等元信息
- 允许后续扩展，但不强迫当前主链提前吸收全部字段

---

## 3. 候选结构

```json
{
  "nodeId": "nd_3f7a9b2c1e5d",
  "documentId": "doc_123",
  "nodeType": "TEXT",
  "indexContent": "2023年第四季度苹果公司营收约1000亿美元...",
  "displayContent": "2023年第四季度苹果公司营收约1000亿美元...",
  "parentNodeId": null,
  "childNodeIds": [],
  "metadata": {
    "sourceFile": "report.pdf",
    "fileType": "pdf",
    "pageNumber": 12,
    "h1": "财务数据",
    "h2": "Q4营收",
    "h3": null,
    "chunkIndex": 3,
    "totalChunks": 10,
    "tokenCount": 680,
    "tokenizerType": "qwen_v3_base_local",
    "embeddingModel": "text-embedding-v3",
    "language": "zh",
    "quality": "high",
    "createdAt": "2025-03-27T10:00:00Z",
    "updatedAt": "2025-03-27T10:00:00Z"
  }
}
```

---

## 4. 字段语义

### 4.1 顶层字段

| 字段 | 说明 |
| --- | --- |
| `nodeId` | 节点唯一标识 |
| `documentId` | 对应文档资产标识 |
| `nodeType` | 候选节点类型，如 `TEXT` / `TABLE` / `CODE` / `IMAGE` |
| `indexContent` | 面向 embedding / retrieval 的索引内容 |
| `displayContent` | 面向 LLM / 引用展示的内容 |
| `parentNodeId` | 父节点标识；无父节点时为空 |
| `childNodeIds` | 子节点标识集合 |
| `metadata` | 节点的非内容维度元信息 |

### 4.2 `metadata` 候选字段

| 字段 | 说明 |
| --- | --- |
| `sourceFile` | 来源文件名 |
| `fileType` | 来源文件类型 |
| `pageNumber` | 所在页码；仅在格式天然有分页语义时考虑 |
| `h1/h2/h3` | 标题路径 |
| `chunkIndex` | 文档内块序号 |
| `totalChunks` | 文档总块数 |
| `tokenCount` | 节点文本 token 数 |
| `tokenizerType` | 分词器标识 |
| `embeddingModel` | 向量模型名 |
| `language` | 语言标记 |
| `quality` | 质量标记 |
| `createdAt/updatedAt` | 时间戳 |

---

## 5. 与当前系统的关系

当前系统现实情况是：

- 正式中间文本产物仍是 `cleaned.md`
- 当前 `processingMetadata` 是文档级处理结果元数据，不是最终节点契约
- 当前 `RetrievedChunk` / `AskReferenceResponse` 仍是较薄的现行契约
- 当前 `vector metadata shape` 同时承担检索与幂等控制职责，不能轻率扩写

因此，这份草案在正式落地前必须先回答：

1. 哪些字段属于 chunker 内部事实
2. 哪些字段属于向量元数据稳定字段
3. 哪些字段属于 retrieval 内部结果
4. 哪些字段需要真正进入对外 reference DTO

---

## 6. 当前不落地的原因

当前不把这份草案直接并入本轮 `ingest-cleaning`，原因是：

- 本轮主目标是高质量 `cleaned.md`
- 父子分块尚未定型
- 过早升级节点契约会把 parser / cleaner 优化和 retrieval / reference 升级绑死
- 当前仓库还存在文档版本治理与 RAG 优化的共享高冲突区

---

## 7. 后续进入实现前需要补的决策

在正式进入实现前，至少还需要补齐这些决策：

- 父子分块的切分规则
- 父节点与子节点的索引策略
- `indexContent` 与 `displayContent` 的差异生成规则
- 节点级页码与来源定位策略
- 与 `document version`、`askable version` 的关系
- 对外引用 DTO 到底暴露哪些字段

---

## 8. 一句话定位

本文档只是“未来父子分块与 richer node model 可能长什么样”的候选草案，不代表当前实现契约，也不代表本轮 `ingest-cleaning` 的完成条件。
