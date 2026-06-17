# Investigation: Docling 响应缺少 Markdown Content

## Hand-off Brief

1. **What happened.** 文档上传与处理链路执行成功，但 Spring 端记录 `content` 各格式为空，只能降级使用 `chunks` 文本；Docling 容器同时返回两次 `POST /v1/chunk/hybrid/source` 的 `200` 响应。
2. **Where the case stands.** 该问题已确认不是 Spring 端读取错误，也不是任务失败；根因收敛为当前 `Docling Serve` 的 `/v1/chunk/hybrid/source` 路径虽然成功返回 chunks，但没有把 converted document content 填进响应。该根因现已按“转换/分块双调用”策略修复。
3. **What's needed next.** 当前仓库已落地修复：`DoclingDocumentParser` 改走 `/v1/convert/source`，`DoclingDocumentChunker` 保持走 `/v1/chunk/hybrid/source`，`ProcessDocumentApplicationService` 继续执行“先 parse、后 chunk”的两阶段流程。后续仅在需要向上游追 issue 时，再继续调查 hybrid endpoint 的实现差异。

## Case Info

| Field            | Value |
| ---------------- | ----- |
| Ticket           | N/A |
| Date opened      | 2026-06-17 |
| Status           | Closed (Fixed) |
| System           | Windows 11, Spring Boot 3.5.8, Java 21, Docling Serve 容器 |
| Evidence sources | 用户提供的 Spring 日志、用户提供的 Docling 容器日志、`docs/reference/Docling/openapi.json`、本地源码、旧 investigation artifacts |

## Problem Statement

用户报告：上传 Markdown 文档 `人工智能基础概念.md` 后，系统完成入库和向量化，但未能将文档转换为 markdown content。Spring 日志显示：

```text
Docling 所有 content 格式均为空，降级使用 chunks 文本 (status=success, chunks=1)
```

用户希望确认为什么“无法把文档转成 md 格式”，并允许参考旧的 investigations 与本地保存的 Docling OpenAPI。

## Resolution Update

- 已确认 `ProcessDocumentApplicationService` 的业务流程本来就是“解析正文 → 保存中间产物 → 分块 → 向量化”，问题不在应用层编排。
- 已将 `DoclingDocumentParser` 调整为调用 `DoclingServeApi.convertSource(...)`，对应 Docling Serve 的 `/v1/convert/source`，仅负责“源文件 -> cleanedMarkdown”。
- `DoclingDocumentChunker` 继续调用 `DoclingServeApi.chunkSourceWithHybridChunker(...)`，对应 `/v1/chunk/hybrid/source`，仅负责结构化分块。
- `DoclingDocumentParserTest` 已按新 API 同步，当前 25 个单元用例通过。
- 以下 `Findings`、`Deductions`、`Hypotheses` 保留的是修复前现场证据；调查时使用过的临时直调脚本和临时测试文档现已清理，不再作为仓库产物保留。

## Evidence Inventory

| Source | Status | Notes |
| ------ | ------ | ----- |
| Spring 服务日志 | Available | 用户已提供完整时间线与关键日志 |
| Docling 容器日志 | Available | 用户已提供两次 `/v1/chunk/hybrid/source` 200 响应 |
| 本地源码 | Available | 待沿调用链核对请求构造和响应解析 |
| Docling OpenAPI | Available | `docs/reference/Docling/openapi.json`，来自运行中的 docling-serve |
| 旧 investigations | Available | 可参考，但不视为既定结论 |

## Investigation Backlog

| # | Path to Explore | Priority | Status | Notes |
| - | --------------- | -------- | ------ | ----- |
| 1 | 核对 Spring 端 Docling 请求构造与响应解析 | High | Done | 本地代码请求与字段读取逻辑已核对 |
| 2 | 核对 OpenAPI 中 `include_converted_doc` 与 content 字段定义 | High | Done | 契约明确要求同时返回 chunks 和 converted doc |
| 3 | 对照旧 investigation 结论与当前证据 | Medium | Done | 旧结论中“服务端行为异常”被当前证据支持 |
| 4 | 必要时查询官方文档或库文档 | Medium | Done | Context7 官方文档已补充 |

## Timeline of Events

| Time | Event | Source | Confidence |
| ---- | ----- | ------ | ---------- |
| 2026-06-17T21:58:18.179+08:00 | Spring 接受上传请求并记录 documentId、filename、fileHash | 用户提供的 Spring 日志 | Confirmed |
| 2026-06-17T21:58:19.736+08:00 | Spring worker 认领文档并进入处理流水线 | 用户提供的 Spring 日志 | Confirmed |
| 2026-06-17T21:58:19 ~ 21:58:21+08:00 | Docling 容器处理 `人工智能基础概念.md`，生成 1 个 chunk，并返回 `/v1/chunk/hybrid/source` 200 | 用户提供的 Docling 日志 | Confirmed |
| 2026-06-17T21:58:21.757+08:00 | Spring 记录 Docling 所有 content 格式为空，降级使用 chunks 文本 | 用户提供的 Spring 日志 | Confirmed |
| 2026-06-17T21:58:24.043+08:00 | Spring 完成向量索引与文档处理 | 用户提供的 Spring 日志 | Confirmed |
| 2026-06-17 | 本地通过临时直调脚本（现已清理）命中同一 hybrid chunk endpoint，返回 `Documents=1` 且 `md/html/text/doctags` 全为 NULL | 本次调查的直接 HTTP 复现 | Confirmed |
| 2026-06-17 | 同一份临时测试 Markdown 文档（现已清理）改打 `/v1/convert/source` 后，`md_content/html_content/text_content/doctags_content` 全部返回非空 | 本次调查的直接 HTTP 对照实验 | Confirmed |

## Confirmed Findings

### Finding 1: 当前症状发生在“处理成功后的内容提取阶段”

**Evidence:** 用户提供的 Spring 日志 `2026-06-17T21:58:21.757+08:00`

**Detail:** 日志同时包含 `status=success` 与 “所有 content 格式均为空”，说明问题不是任务失败，而是成功响应中缺少 Spring 端期望的内容字段。

### Finding 2: Spring 端确实请求了 converted document

**Evidence:** 修复前 `DoclingDocumentParser` 的请求构造逻辑（已由后续修复替换）

**Detail:** 调查时 `DoclingDocumentParser` 构造 `HybridChunkDocumentRequest`，明确设置 `toFormats=[MARKDOWN, HTML, TEXT, DOCTAGS]`，并设置 `includeConvertedDoc(true)` 后调用 `doclingServeApi.chunkSourceWithHybridChunker(request)`。这解释了为什么应用会依赖 hybrid endpoint 返正文。

### Finding 3: Spring 端不是只会读 `md_content`

**Evidence:** `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java:252-283`

**Detail:** 代码按 `md_content -> html_content -> text_content -> doctags_content -> chunks` 逐级降级，只有所有 converted content 都为空时才退回 chunks。当前日志命中的正是这条最终降级路径。

### Finding 4: 本地单元测试已经把“content 全空但 chunks 有值”视为允许场景

**Evidence:** 修复前的 `DoclingDocumentParserTest` 场景设计（已由后续修复替换）

**Detail:** 调查时的测试 `parse_shouldFallbackToChunks_whenAllContentFormatsNullButChunksPresent()` 明确构造了 `status=success`、`content={}`、`chunks` 非空的响应，并断言解析器应回退到 chunks。这说明修复前代码库已经接受“hybrid chunk 成功但无 converted content”的现实行为。

### Finding 5: 运行中的 Docling OpenAPI 期望 hybrid chunk 接口在 `include_converted_doc=true` 时返回 converted document

**Evidence:** `docs/reference/Docling/openapi.json:311-345`, `docs/reference/Docling/openapi.json:4595-4599`, `docs/reference/Docling/openapi.json:4156-4177`

**Detail:** 本地下载的 OpenAPI 指明 `/v1/chunk/hybrid/source` 返回 `ChunkDocumentResponse`；`include_converted_doc` 的描述是“如果为 true，输出将同时包含 chunks 和 converted document”；`DocumentResultItem` 中 `content` 与 `status` 都是 required 字段。

### Finding 6: Docling 官方文档对 converted document 的预期与本地 OpenAPI 一致

**Evidence:** Context7 `Docling Serve` 文档 `docs/usage.md`

**Detail:** 官方文档说明单文件 JSON 响应会包含 `md_content/json_content/html_content/text_content/doctags_content`，哪些字段有值取决于 `output_formats`。

### Finding 7: 直接调用 hybrid chunk endpoint 时，服务端原始 JSON 的 converted content 确实为空

**Evidence:** 本地通过临时直调脚本（现已清理）直接访问 Docling Serve

**Detail:** 同一仓库中的测试脚本向 `POST http://localhost:5001/v1/chunk/hybrid/source` 发送 `include_converted_doc=true` 与 `to_formats=["md","html","text","doctags"]` 后，原始响应显示 `Documents=1`、`Status=success`，但 `md_content/html_content/text_content/doctags_content` 全部为 `NULL`。

### Finding 8: 同一份 Markdown 文档通过 convert endpoint 可以正常返回 `md_content`

**Evidence:** 本次调查中直接调用 `POST http://localhost:5001/v1/convert/source`

**Detail:** 对同一份临时测试 Markdown 文档（现已清理），`/v1/convert/source` 返回 `md_content PRESENT 388`、`html_content PRESENT 4438`、`text_content PRESENT 388`、`doctags_content PRESENT 710`。这排除了“Docling 根本不能把这份 Markdown 转成 md”的说法。

## Deduced Conclusions

### Deduction 1: 问题不在任务调度、向量入库或 Spring 端字段选择逻辑

**Based on:** Finding 1, Finding 2, Finding 3

**Reasoning:** 上传、认领、解析流水线推进、向量更新与最终成功日志都已出现；同时 Spring 端已明确请求 converted content，且不会只读 `md_content`。因此故障面不在后续流程，而在 Docling hybrid chunk 响应边界。

**Conclusion:** Spring 端的“无法转成 md”不是本地解析器 bug，而是上游 hybrid chunk 响应没带 converted content。

### Deduction 2: 这不是“Docling 无法转换 Markdown”，而是“hybrid chunk endpoint 没返回 converted content”

**Based on:** Finding 7, Finding 8

**Reasoning:** 同一份 `test-doc.md` 在 `/v1/chunk/hybrid/source` 中 `content.*` 全空，但在 `/v1/convert/source` 中 `md_content/html_content/text_content/doctags_content` 全部非空。输入文档与服务实例都相同，唯一变化是 endpoint。

**Conclusion:** 根因是 endpoint 语义/实现差异，而不是 Markdown 输入本身不可转换。

### Deduction 3: 当前代码路径把 chunking 与 conversion 绑在同一个 hybrid endpoint 上，这是导致“拿不到 markdown 正文”的直接机制

**Based on:** Finding 2, Finding 7, Finding 8

**Reasoning:** 解析器当前只调用 `chunkSourceWithHybridChunker()`，并把它既当 chunk 来源，又当正文来源；而实际服务行为是该 endpoint 虽然返回 chunks，却没有返回 converted content。

**Conclusion:** 如果业务需要稳定的 markdown 正文，当前单接口策略与运行中的 Docling 服务行为不匹配。

## Hypothesized Paths

### Hypothesis 1: Docling 服务返回了成功状态和 chunks，但未返回 converted document content

**Status:** Confirmed

**Theory:** `/v1/chunk/hybrid/source` 在当前 Docling 版本下可能仅保证分块结果，不保证 `content.md_content` 等字段存在。

**Supporting indicators:** 用户提供的容器日志显示 chunking 成功，但 Spring 端仍读到空 content。

**Would confirm:** 实际响应 JSON 中这些字段为 `null` 或缺失。

**Would refute:** 实际响应 JSON 中这些字段非空，只是 Spring 端读取错了。

**Resolution:** 已通过本地临时直调脚本（现已清理）确认 hybrid chunk endpoint 的原始响应中 `md/html/text/doctags` 全为 `NULL`。

### Hypothesis 2: Spring 端读取的字段路径或格式优先级与 Docling 当前响应结构不一致

**Status:** Refuted

**Theory:** Docling 可能把 markdown 放在与当前代码不同的字段中，导致本地代码误判为“所有 content 格式为空”。

**Supporting indicators:** 用户问题聚焦“无法转成 md”，而不是“没有 chunk”，说明有可能是字段契约偏移而非服务失效。

**Would confirm:** OpenAPI 或源码表明当前接口返回内容字段与本地模型或提取逻辑不一致。

**Would refute:** 本地代码和 OpenAPI 完全匹配，且原始响应确实无 markdown 字段。

**Resolution:** 已核对 `DoclingDocumentParser` 的请求与读取逻辑，并用原始 HTTP 调用确认服务端直接返回空内容；Spring 端没有读错字段。

## Missing Evidence

| Gap | Impact | How to Obtain |
| --- | ------ | ------------- |
| Docling Serve hybrid chunk endpoint 为何不填充 `content.*` | 只能进一步区分“已知限制”还是“服务端缺陷” | 继续查看上游 Docling Serve 实现或变更日志 |

## Source Code Trace

| Element | Detail |
| ------- | ------ |
| Error origin | 修复前 `DoclingDocumentParser.parse()` 的正文提取路径 |
| Trigger | 修复前 `DoclingDocumentParser.parse()` 调用 `chunkSourceWithHybridChunker()` 后尝试从 `documents[0].content` 提取正文 |
| Condition | `POST /v1/chunk/hybrid/source` 返回 `documents[0].status=success`，但 `documents[0].content.md/html/text/doctags` 全为空 |
| Current fix | `DoclingDocumentParser.parse()` 现改为调用 `convertSource()` 提取正文；`DoclingDocumentChunker.chunk()` 继续调用 `chunkSourceWithHybridChunker()` 提取 chunks |
| Related files | `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java`, `src/main/java/io/github/spike/myai/ingest/infrastructure/chunking/DoclingDocumentChunker.java`, `src/test/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParserTest.java`, `docs/reference/Docling/openapi.json` |

## Conclusion

**Confidence:** High

从当前系统的角度，根因已经可以明确：你的后端把 `/v1/chunk/hybrid/source` 同时当作“分块接口”和“markdown 正文接口”来用，但运行中的 Docling Serve 在这条 endpoint 上虽然成功返回 chunks，却没有返回 converted document content。  
这不是 Spring 端字段读取错误，也不是“Docling 不能把 Markdown 转成 Markdown”。同一份 `test-doc.md` 直接走 `/v1/convert/source` 时，`md_content/html_content/text_content/doctags_content` 都能正常返回。  
因此，症状“无法把文档转成 md 格式”准确地说是：**当前选用的 hybrid chunk endpoint 没有给你返回 md content，所以应用只能退回 chunks 文本。**

## Recommended Next Steps (Investigation Time)

### Fix direction

1. 如果你的业务首要目标是拿到稳定正文，正文转换与正文存档应走 `/v1/convert/source`。
2. 如果你的业务首要目标是拿到结构化 chunks，`/v1/chunk/hybrid/source` 仍可继续用于 chunk 生成。
3. 如果坚持单接口完成“转换 + 分块”，需要进一步验证/升级 Docling Serve，确认 `include_converted_doc` 在当前版本是否存在实现缺陷。

### Diagnostic

1. 复用临时直调脚本（调查时使用，现已清理）作为最小复现，保留对 hybrid chunk endpoint 的直接证据。
2. 如需向上游提 issue，再补一条 `curl` 复现和 Docling Serve 版本号。
3. 如需本仓库内修复，优先评估“双调用策略”或“转换/分块职责拆分”。

## Reproduction Plan

1. 使用任意最小 Markdown 样本文档（调查时使用过临时 `test-doc.md`，现已清理）。
2. 调用 `POST /v1/chunk/hybrid/source`，请求体带 `include_converted_doc=true` 与 `to_formats=["md","html","text","doctags"]`。
3. 预期结果：`chunks` 非空，`documents[0].status=success`，但 `documents[0].content.md_content/html_content/text_content/doctags_content` 均为空。
4. 对照调用 `POST /v1/convert/source`，预期 `document.md_content/html_content/text_content/doctags_content` 均非空。

## Side Findings

- 旧 investigations 中“服务端行为异常/版本偏差”的方向与本次新证据一致，但本案卷的结论是基于新的直接 HTTP 复现得出。
- `DocumentResultItem.content` 在本地 OpenAPI 中是 required，但 `ExportDocumentResponse` 内各具体内容字段可为 `null`；当前服务端满足前者，却在关键字段上全部给出空值。

## Follow-up: 2026-06-17

### New Evidence

- 本地通过临时直调脚本（现已清理），确认 `POST /v1/chunk/hybrid/source` 的原始响应中 `md/html/text/doctags` 全部为 `NULL`。
- 同一份临时测试 Markdown 文档（现已清理）直接调用 `POST /v1/convert/source`，确认 `md_content/html_content/text_content/doctags_content` 全部返回非空。

### Additional Findings

- 当前运行中的 Docling OpenAPI 与官方文档都声称 `include_converted_doc=true` 应该同时包含 converted document，但 hybrid chunk endpoint 的实际行为与该预期不一致。

### Updated Hypotheses

- Hypothesis 1 已 Confirmed。
- Hypothesis 2 已 Refuted。

### Backlog Changes

- 关闭“Spring 端读取逻辑是否错误”的排查项。
- 保留“是否需要继续追到 Docling Serve 上游实现/版本差异”的可选项。

### Updated Conclusion

当前问题的直接根因是 endpoint 选择与服务行为不匹配：`/v1/chunk/hybrid/source` 在你的运行环境下不会提供可用的 converted markdown content，而 `/v1/convert/source` 会。

## Final Resolution

- 已落地“转换/分块双调用”修复：
  - `DoclingDocumentParser` 调用 `DoclingServeApi.convertSource(...)`，仅负责正文转换和 `cleanedMarkdown` 产出。
  - `DoclingDocumentChunker` 调用 `DoclingServeApi.chunkSourceWithHybridChunker(...)`，仅负责结构化分块。
  - `ProcessDocumentApplicationService` 保持 `parse -> save artifact -> chunk -> index` 的两阶段处理顺序。
- 结果是应用不再依赖 hybrid chunk endpoint 返回正文，因此“无法把文档转成 md 格式”的问题已关闭。
- 修复验证：`DoclingDocumentParserTest` 已同步到 Convert API，25 个单元测试通过。
