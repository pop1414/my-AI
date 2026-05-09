# RAG 文档节点标准数据契约

**版本**：1.0
**适用范围**：RAG 二期及以后阶段使用的统一 JSON 节点，供向量存储、检索增强生成（RAG）以及后续多模态扩展使用。
**设计原则**：schema 稳定可扩展、字段语义明确、支持从纯文本到多模态的零破坏升级。

> 阶段说明：**本契约不是一期“纯文本解析与清洗”阶段的主输出。**
> 一期主输出应为 `cleaned.md` 等中间文本产物，以及轻量级解析元数据；待二期引入正式分块后，再由分块模块生成符合本契约的节点对象。

---

## 1\. JSON 结构总览

```json
{
	"node_id": "nd_3f7a9b2c1e5d",
	"doc_id": "doc_998877",
	"node_type": "text",

	"index_content": "2023年第四季度苹果公司营收约1000亿美元...",
	"display_content": "2023年第四季度苹果公司营收约1000亿美元...",

	"parent_id": null,
	"child_ids": [],

	"metadata": {
		"source_file": "2023_Q4_Report.pdf",
		"file_type": "pdf",
		"page_num": 12,
		"h1": "财务数据",
		"h2": "Q4营收",
		"h3": "产品线明细",
		"keywords": ["苹果", "营收", "Q4", "iPhone"],
		"chunk_index": 45,
		"total_chunks": 120,
		"token_count": 86,
		"tokenizer_type": "qwen_v3_base",
		"embedding_model": "text-embedding-v3",
		"language": "zh",
		"quality": "high",
		"created_at": "2025-03-27T10:23:45Z",
		"updated_at": "2025-03-27T10:23:45Z"
	}
}
```

---

## 1.1 与一期中间产物的关系

为避免阶段职责冲突，项目按以下方式分层：

- **一期（纯文本解析与清洗）**：输出 `cleaned.md`、可选的 `raw.xhtml` / `cleaned.html`，以及轻量级解析元数据
- **二期（分块与节点生成）**：读取一期中间产物，生成本契约定义的 node JSON
- **三期及以后**：在不破坏旧字段的前提下扩展表格、图片、多模态节点

因此，本契约中的 `chunk_index`、`parent_id`、`child_ids`、`node_type` 多类型能力，均以二期节点生成成功为前提。

---

## 2\. 字段详细定义

### 2.1 顶层字段

| 字段              | 类型          | 必填 | 说明                                                                       |
| ----------------- | ------------- | ---- | -------------------------------------------------------------------------- |
| `node_id`         | string        | ✅   | 全库唯一的节点标识，建议使用带时间戳的 UUID 或 `nd_` 前缀哈希。            |
| `doc_id`          | string        | ✅   | 指向原始文档的唯一 ID，用于溯源和去重更新。                                |
| `node_type`       | enum          | ✅   | 节点内容类型：`text`、`table`、`code`、`image`。二期首版默认使用 `text`。  |
| `index_content`   | string        | ✅   | 用于计算 `Embedding` 的文本内容。纯文本节点通常与 `display_content` 相同。 |
| `display_content` | string        | ✅   | 交给 LLM 的最终上下文内容。纯文本节点通常与 `index_content` 相同。         |
| `parent_id`       | string        | ❌   | 若采用父子分块 (Small-to-Big)，填写父块节点 ID；否则为 `null`。            |
| `child_ids`       | array[string] | ❌   | 若当前节点为父块，存放所有子块 ID；否则为空数组。                          |
| `metadata`        | object        | ✅   | 承载所有非内容维度的元信息，是检索过滤和展示来源的核心。                   |

### 2.2 metadata 子字段

为降低实现复杂度，`metadata` 子字段分为三层：

- **稳定输出**：分块后基本都能稳定产出，建议优先作为契约必备信息
- **条件输出**：依赖源文档结构、解析质量或额外算法，允许为空
- **前瞻预留**：为未来模型、多模态或复杂检索策略保留，一期/二期可暂不启用

| 子字段            | 类型          | 分层     | 说明                                                                                    |
| ----------------- | ------------- | -------- | --------------------------------------------------------------------------------------- |
| `source_file`     | string        | 稳定输出 | 原始文件名，用于在前端展示来源。                                                        |
| `file_type`       | string        | 稳定输出 | 文件扩展名：`pdf`、`docx`、`md`、`html` 等。                                            |
| `chunk_index`     | int           | 稳定输出 | 该节点在全文档中的切块序号（从 1 开始），便于相邻块上下文扩展。                         |
| `language`        | string        | 条件输出 | ISO 639-1 语言代码：`zh`、`en` 等；在混排或弱结构文档中允许为空或作为弱保证输出。       |
| `quality`         | string        | 稳定输出 | 文档解析质量标签：`high`（正常）、`low`（弱结构/扫描件）、`warning`（长度或格式风险）。 |
| `created_at`      | string        | 稳定输出 | ISO 8601 UTC 时间戳，用于增量更新与缓存管理。                                           |
| `updated_at`      | string        | 稳定输出 | 最后修改时间，同上。                                                                    |
| `page_num`        | int           | 条件输出 | 所在页码（仅当文档有明确分页且可稳定提取时）。                                          |
| `h1`, `h2`, `h3`  | string        | 条件输出 | 所在章节的一、二、三级标题，依赖标题结构可识别。                                        |
| `total_chunks`    | int           | 条件输出 | 源文档被切成的总块数，可帮助计算相对位置。                                              |
| `token_count`     | int           | 条件输出 | `index_content` 的 Token 估算值，用于成本监控和长度过滤。                               |
| `tokenizer_type`  | string        | 条件输出 | 记录 Token 计数所使用的分词器标识（如 `qwen_v3_base`）。                                |
| `keywords`        | array[string] | 前瞻预留 | 从文本中提取的关键实体词，可用于稀疏检索、粗排或生成高光片段。                          |
| `embedding_model` | string        | 前瞻预留 | 生成 Embedding 时所使用的模型名称，便于后续失效重建。                                   |

---

## 3\. 不同节点类型的示例

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
		"total_chunks": 10,
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

### 3.2 表格节点

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

## 4\. 与阿里 DashScope 模型对齐的要点（前瞻预留）

- **Embedding 计数一致性**：若项目接入 DashScope TokenizationService，建议记录 `tokenizer_type = "qwen_v3_base"`，确保与 `text-embedding-v3` 窗口（最大 2048）兼容。
- **安全冗余**：分块时若会在子块内容前注入标题路径（`h1 > h2 > h3`），建议预留约 15% 的 Token 空间。
- **图片 Token 预算**：节点类型为 `image` 时，可为未来视觉模型保留预算策略；该策略不作为一期纯文本阶段的硬要求。

---

## 5\. 扩展性说明

此 Schema 的设计支持以下未来升级而无需破坏旧数据：

- **新增 node_type**：只需扩展枚举值，旧节点保持不变。
- **更丰富的元数据**：可在 `metadata` 中添加新的可选字段；下游消费者应具备忽略未知字段的能力。
- **多模态融合**：图片节点的 `index_content` 与 `display_content` 已经规划为描述性文本，可直接对接 `qwen3-vl` 系列模型。
- **父子分块**：`parent_id` / `child_ids` 为空时，系统视为无层级；填充后即可启用 `Small-to-Big` 检索策略。

---

此数据契约覆盖了 RAG 文档处理的全生命周期，从纯文本到多模态、从单块到层级均可零破坏性扩展，建议作为项目开发的标准接口文件存档。
