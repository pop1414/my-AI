# RAG 文档节点标准数据契约（二期产物）

**版本**：1.0

**适用范围**：RAG 管线中用于向量存储与检索增强生成的标准节点格式

**设计原则**：稳定可扩展、字段语义明确、支持纯文本到多模态的平滑升级

---

## 1\. JSON 结构总览

```json
{
	"node_id": "nd_3f7a9b2c1e5d",
	"doc_id": "doc_123",
	"node_type": "text",
	"index_content": "2023年第四季度苹果公司营收约1000亿美元...",
	"display_content": "2023年第四季度苹果公司营收约1000亿美元...",
	"parent_id": null,
	"child_ids": [],

	"metadata": {
		"source_file": "report.pdf",
		"file_type": "pdf",
		"page_num": 12,
		"h1": "财务数据",
		"h2": "Q4营收",
		"h3": null,
		"keywords": ["苹果", "营收", "Q4"],
		"chunk_index": 3,
		"total_chunks": 10,
		"token_count": 680,
		"tokenizer_type": "qwen_v3_base",
		"embedding_model": "text-embedding-v3",
		"language": "zh",
		"quality": "high",
		"created_at": "2025-03-27T10:00:00Z",
		"updated_at": "2025-03-27T10:00:00Z"
	}
}
```

---

## 2\. 字段详细定义

### 2.1 顶层字段

| 字段              | 类型          | 必填 | 说明                                                                                                     |
| ----------------- | ------------- | ---- | -------------------------------------------------------------------------------------------------------- |
| `node_id`         | string        | ✅   | 全库唯一节点标识，建议 `nd_` 前缀 + 时间戳哈希。                                                         |
| `doc_id`          | string        | ✅   | 指向原始文档的唯一 ID，用于溯源与去重更新。                                                              |
| `node_type`       | enum          | ✅   | 节点类型：`text`、`table`、`code`、`image`。纯文本阶段统一为 `text`。                                    |
| `index_content`   | string        | ✅   | 用于计算 Embedding 的文本内容。普通文本时与 `display_content` 相同；表格时为语义摘要；图片时为视觉描述。 |
| `display_content` | string        | ✅   | 交给 LLM 的最终上下文内容。表格时保留完整 Markdown 格式；代码时保留原始格式；图片时为描述 + 引用标记。   |
| `parent_id`       | string        | ❌   | 若有父子分块，存放父块节点 ID；否则为 `null`。                                                           |
| `child_ids`       | array[string] | ❌   | 若为父块，则包含所有子块 ID；否则为空数组。                                                              |
| `metadata`        | object        | ✅   | 承载非内容维度的元信息，是检索过滤和展示来源的核心。                                                     |

### 2.2 `metadata` 子字段

| 子字段            | 类型          | 必填 | 说明                                                                                         |
| ----------------- | ------------- | ---- | -------------------------------------------------------------------------------------------- |
| `source_file`     | string        | ✅   | 原始文件名，用于前端展示来源。                                                               |
| `file_type`       | string        | ❌   | 文件扩展名。                                                                                 |
| `page_num`        | int           | ❌   | 所在页码（有明确分页时填写）。                                                               |
| `h1`, `h2`, `h3`  | string        | ❌   | 该节点所在章节的一、二、三级标题。                                                           |
| `keywords`        | array[string] | ❌   | 关键实体词，用于粗排或高亮展示。                                                             |
| `chunk_index`     | int           | ✅   | 该节点在文档中的切块序号（从 1 开始）。**二期分块器填充**，一期 `meta.json` 中不存在此字段。 |
| `total_chunks`    | int           | ❌   | 文档总块数。二期填充。                                                                       |
| `token_count`     | int           | ❌   | `index_content` 的 Token 数，由阿里分词器计算。                                              |
| `tokenizer_type`  | string        | ❌   | 分词器标识，固定 `"qwen_v3_base"`。                                                          |
| `embedding_model` | string        | ❌   | 生成 Embedding 使用的模型名（如 `text-embedding-v3`），切换模型时可失效重建。                |
| `language`        | string        | ❌   | 语言代码，可填 `"mixed"`。                                                                   |
| `quality`         | string        | ❌   | 解析质量：`high`、`low`、`warning`、`oversized`。                                            |
| `created_at`      | string        | ❌   | ISO 8601 时间戳，用于增量更新与缓存。                                                        |
| `updated_at`      | string        | ❌   | 最后修改时间。                                                                               |

---

## 3\. 不同节点类型示例

### 3.1 纯文本节点

```json
{
	"node_id": "nd_a1b2c3d4",
	"doc_id": "doc_123",
	"node_type": "text",
	"index_content": "System architecture consists of three layers...",
	"display_content": "System architecture consists of three layers...",
	"parent_id": null,
	"child_ids": [],
	"metadata": {
		"source_file": "architecture_overview.md",
		"file_type": "md",
		"page_num": null,
		"h1": "系统架构",
		"h2": "分层设计",
		"h3": null,
		"keywords": ["系统架构", "分层", "可扩展"],
		"chunk_index": 1,
		"total_chunks": 2,
		"token_count": 120,
		"tokenizer_type": "qwen_v3_base",
		"embedding_model": "text-embedding-v3",
		"language": "zh",
		"quality": "high",
		"created_at": "2025-03-27T08:00:00Z",
		"updated_at": "2025-03-27T08:00:00Z"
	}
}
```

### 3.2 表格节点（二期可扩展）

```json
{
	"node_id": "nd_t1a2b3",
	"doc_id": "doc_456",
	"node_type": "table",
	"index_content": "季度营收表：Q1 200万, Q2 350万, Q3 280万, Q4 400万",
	"display_content": "| 季度 | 营收(万元) |\n|------|----------|\n| Q1   | 200      |\n| Q2   | 350      |\n| Q3   | 280      |\n| Q4   | 400      |",
	"parent_id": null,
	"child_ids": [],
	"metadata": {
		"source_file": "financial_report.xlsx",
		"file_type": "xlsx",
		"page_num": null,
		"h1": "财务数据",
		"h2": "收入分析",
		"h3": "季度细分",
		"keywords": ["营收", "季度", "财务"],
		"chunk_index": 5,
		"total_chunks": 30,
		"token_count": 45,
		"tokenizer_type": "qwen_v3_base",
		"embedding_model": "text-embedding-v3",
		"language": "zh",
		"quality": "high",
		"created_at": "2025-03-27T09:15:00Z",
		"updated_at": "2025-03-27T09:15:00Z"
	}
}
```

### 3.3 图片节点（多模态预留）

```json
{
	"node_id": "nd_img001",
	"doc_id": "doc_789",
	"node_type": "image",
	"index_content": "图1：2023年销售趋势折线图，Q1 200万，Q2 350万，Q3 280万，Q4 400万，整体呈上升趋势。",
	"display_content": "[图片描述] 2023年销售趋势折线图，Q1 200万，Q2 350万，Q3 280万，Q4 400万。 [原始图片URL: https://xxx.com/img_001.png]",
	"parent_id": null,
	"child_ids": [],
	"metadata": {
		"source_file": "sales_presentation.pptx",
		"file_type": "pptx",
		"page_num": 3,
		"h1": "销售分析",
		"h2": "年度趋势",
		"h3": null,
		"keywords": ["销售趋势", "折线图", "QoQ增长"],
		"chunk_index": 12,
		"total_chunks": 40,
		"token_count": 448,
		"tokenizer_type": "qwen_v3_base",
		"embedding_model": "text-embedding-v3",
		"language": "zh",
		"quality": "high",
		"created_at": "2025-03-27T10:00:00Z",
		"updated_at": "2025-03-27T10:00:00Z"
	}
}
```

---

## 4\. 与阿里 DashScope 模型对齐要点

- **Embedding 计数一致性**：`token_count` 使用阿里官方 `qwen_v3_base` 分词器计算，保证与 `text-embedding-v3` 窗口（2048）兼容。
- **安全冗余**：分块时在子块内容前注入标题路径（`h1>h2>h3`），为此预留 15% 的 Token 空间；`token_count` 仅计算纯内容。
- **图片 Token 预算**：节点类型为 `image` 时，`token_count` 填写 448（视觉模型固定占位），确保多模态扩展时预算一致。
- **超大文档处理**：一期仅输出全文 token 总数，标记 `quality` 状态；二期分块器会安全切分为多个符合窗口的子块。

---

## 5\. 扩展性说明

- **新增 node_type**：扩展枚举值即可，旧节点保持不变。
- **更丰富的元数据**：可在 `metadata` 中添加新的可选字段；下游消费者应具备忽略未知字段的能力。
- **多模态融合**：图片节点的 `index_content` 与 `display_content` 已规划为描述性文本，可直接对接 `qwen3‑vl` 系列模型。
- **父子分块**：`parent_id` / `child_ids` 为空时表示单层节点；填充后即可启用 `Small‑to‑Big` 检索策略。

---

此计划与数据契约已覆盖文档处理全生命周期，一期输出严格为 `clean.md` + `meta.json`，二期在此之上生成完整的 RAG 节点 JSON，职责清晰、可零破坏性扩展。
