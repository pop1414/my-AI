# Spec: QA 问答与引用

> Parent PRD: 无（从 CONTEXT.md §4.3 提取，对应已实现的系统事实）
> Related ADR: ADR-0005-rag-access-control-foundation.md
> Related SPEC: docs/features/document-versioning/SPEC.md（版本基线）、docs/features/ingest/SPEC.md（处理链路）

## 概述

qa 子域负责问答编排：检索候选内容、构建问答输入、调用模型生成回答、返回结构化引用。qa 消费 ingest 产出的可检索内容，受 knowledge 和授权规则约束，不拥有文档。

## 功能规格

### 用户场景

| # | 角色 | 动作 | 结果 |
|---|------|------|------|
| 1 | KB_READER 及以上 | 在选中知识库内提问 | 返回回答 + 结构化引用 |

> 注：`KB_ASKER` 已废弃（ADR-0005），不再作为新授权选项，qa 的用户场景自此不包含该角色。
| 2 | KB_READER | 从引用侧栏查看问答基线正文 | 返回对应版本的 cleaned.md |
| 3 | 所有角色 | 提问但知识库无可检索内容 | 返回模型兜底回复，无引用，不展示版本提示 |

### 检索基线规则

- 默认只在当前选中的 `knowledge base` 内检索
- 默认只检索每个 `document` 的最新版本
- 当最新版本尚未 `INDEXED`：对该 document 继续检索最近一个已 `INDEXED` 的版本
- 问答基线按 `document` 独立决定，**不定义**"整次回答唯一全局版本"
- 一次问答若同时命中多个 `document`，每个 `document` 都应按自己的当前可问答版本参与检索与引用
- 新最新版本成功 `INDEXED` 后，后续新发起问答自动切换到该版本
- 已返回的历史问答结果不静默改写

### 引用行为规则

- 每条引用必须显式返回自己的来源版本字段，**不得**只返回 `documentId`
- 引用至少包含：`documentId`、`chunkIndex`、`contentPreview`、`sourceVersionNumber`、`sourceUpdatedAt`、`isLatestVersion`、`latestVersionNumber`、`sourceFilename`
- 引用不是最新版本时，必须显式提示当前最新版本号
- 答案正文不默认展示版本更新时间，仅在引用卡片中展示

### Stale Reference 汇总

- 问答响应顶层返回：`hasStaleReferences`、`staleReferenceCount`
- 问答页顶部版本提示：**仅当至少一条引用不是对应 document 最新版本时**显示
- 完全未命中文档引用（仅模型兜底回复）：**不展示**版本提示

### 权限边界

- `qa.ask` **只能**使用当前用户有权访问的文档内容
- 禁止：检索阶段越权召回、展示阶段再"裁剪引用"
- 禁止：问答结果包含用户本不该看到的内容、仅 references 被隐藏

### 职责边界

qa 消费 ingest 产出和 knowledge 授权，不拥有文档，不管理处理状态。

## API 契约

### POST /api/v1/qa/ask

**请求：** `application/json`
```json
{
  "question": "string",
  "kbId": "string"
}
```

**响应字段：**
- `answer`：回答文本
- `references[]`：引用数组，每条含：
  - `documentId`、`chunkIndex`、`contentPreview`
  - `sourceVersionNumber`、`sourceUpdatedAt`
  - `isLatestVersion`、`latestVersionNumber`
  - `sourceFilename`
- `hasStaleReferences`：是否存在非最新版本引用
- `staleReferenceCount`：非最新版本引用数量

**异常映射：**
| 异常 | HTTP 状态码 |
|------|-------------|
| 请求体为空 | 400 |
| 目标知识库不存在 | 400 |
| 知识库已停用 | 409 |
| 参数校验失败 | 400 |
| 无权限访问目标知识库 | 403 |

## 验证

### 自动化验证命令

| 命令 | 预期结果 | 失败处理 |
|------|----------|----------|
| `mvn test -pl . -Dtest="*QaController*"` | 问答接口测试通过 | 修复测试或代码 |
| `mvn test -pl . -Dtest="*RagService*"` | 检索编排测试通过 | 修复测试或代码 |

### 手动 QA 步骤

| 步骤 | 验证点 |
|------|--------|
| 在已 INDEXED 的知识库内提问 | 返回回答 + 引用，引用含版本号 |
| 在含多版本 document 的知识库提问 | 每个 document 独立使用各自的可问答版本 |
| 在无可检索内容的知识库提问 | 返回兜底回复，无引用，无版本提示 |
| 有非最新版本引用时 | 顶部显示版本提示，hasStaleReferences=true |
| 无权限 document 被排除 | 该 document 不参与检索和引用 |

### 安全验证

- 确认检索结果不包含用户无权访问的 document
- 确认引用中不泄露越权内容
- 确认权限变更后新问答立即生效（不依赖缓存过期）

## 待定问题

- SSE 流式问答的引入时机？
- 会话记忆策略（短期/长期）的具体方案？
