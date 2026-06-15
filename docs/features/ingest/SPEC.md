# Spec: Ingest 文档处理主链

> Parent PRD: 无（从 CONTEXT.md §4.1 提取，对应已实现的系统事实）
> Related ADR: ADR-0004-v1-ingest-processing-strategy.md（已 Superseded）、ADR-0007-s3-compatible-document-asset-storage.md、ADR-0008-docling-complex-document-parser-adapter.md
> Related SPEC: docs/features/document-versioning/SPEC.md（版本治理、正文读取契约）

## 概述

ingest 子域负责文档资产从上传到可检索的完整生命周期。本 Spec 定义其核心处理链路：文件类型路由、Docling 统一解析与 chunking、cleaned.md 生成、正文读取契约、存储策略。

## 功能规格

### 用户场景

| # | 角色 | 动作 | 结果 |
|---|------|------|------|
| 1 | KB_CONTRIBUTOR 及以上 | 上传文档（PDF/Word/Markdown/HTML） | 文档进入处理链路，最终产出 cleaned.md 并完成向量化 |
| 2 | KB_READER | 查看问答基线正文 | 返回当前可问答版本的 cleaned.md |
| 3 | 管理人员 | 查看指定历史版本正文 | 返回对应版本的 cleaned.md |
| 4 | 管理人员 | 重处理失败文档 | 重新走完整处理链路 |

### 状态流转

```text
UPLOADED → INGESTING → INDEXED
                     → FAILED

INDEXED|FAILED → (重处理) → INGESTING → ...
```

### 业务规则

#### 文件类型路由

- **所有支持格式**（PDF、DOCX、PPTX、XLSX、图片、Markdown、HTML、TXT）：统一走 `Docling Serve → HybridChunker → chunks (JSON)`，Java 侧仅做结果映射
- **不支持格式**（CSV、EPUB、RTF 等）：上传时直接拒绝

#### cleaned.md 契约

- `cleaned.md` 是整个处理链路的正式中间文本产物
- 存储归属：`document version`，不是 `document`
- 唯一定位：`workspaceId + documentId + versionNumber`
- 正文读取、问答引用核对、chunk preview 都围绕版本级 `cleaned.md` 展开

#### 正文读取

- 统一接口：`GET /api/v1/documents/{documentId}/content`
- 三个 `source` 分支：
  - `LATEST`：读取当前 latest version 的 cleaned.md
  - `ASKABLE_BASELINE`：读取当前 QA 可问答基线版本的 cleaned.md
  - `EXPLICIT_VERSION` + `versionNumber`：读取指定历史版本正文
- 读取来源：**仅从版本级 cleaned.md 读取**，不从源文件实时解析，不从 vector_store chunk 拼接
- 不改变问答基线；查看历史版本不影响后续问答
- `INDEXED` 与 `FAILED` 版本，只要已形成 `cleaned.md` 即可读取
- `DELETED` 文档不开放正文读取
- 首期返回完整 Markdown（字段名 `contentMarkdown`），服务端设最大读取大小上限

### 错误场景

| 场景 | 错误码 | 说明 |
|------|--------|------|
| 版本尚未生成正文 | `CONTENT_NOT_READY` | 处理中，稍后刷新 |
| 版本已完成但正文缺失 | `CONTENT_ARTIFACT_MISSING` | 需重处理或人工排查 |
| 超出最大读取大小 | `CONTENT_TOO_LARGE` | 拒绝读取，不做静默截断 |
| 文档不存在 | `DOCUMENT_NOT_FOUND` | |
| 无权限 | `DOCUMENT_CONTENT_FORBIDDEN` | |
| 指定版本不存在 | `VERSION_NOT_FOUND` | |
| 无权限查看指定版本 | `VERSION_CONTENT_FORBIDDEN` | |

**关键约束**：正文读取链路不得在 artifact 缺失时同步重新解析源文件。缺失由重处理或修复任务处理。

## 技术规格

### API 契约

#### GET /api/v1/documents/{documentId}/content

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | 枚举 | 是 | `LATEST` / `ASKABLE_BASELINE` / `EXPLICIT_VERSION` |
| `versionNumber` | int | 仅 EXPLICIT_VERSION | 必须为正整数 |

**响应字段：**
`documentId`、`versionNumber`、`latestVersionNumber`、`isLatestVersion`、`isAskableVersion`、`source`、`status`、`filename`、`createdAt`、`updatedAt`、`contentMarkdown`、`contentLength`、`truncated`

### 存储策略

- **介质**：S3 兼容对象存储（首期部署实现为 RustFS）
- **配置切换**：`myai.ingest.storage.type=local|s3`（默认 `local`）
- **Bucket**：`myai-documents`
- **Key 规则**（通过 `DocumentStorageKeyResolver` 计算，不落库）：
  - 源文件：`source/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{filename}`
  - 处理产物：`artifacts/{workspaceId}/documents/{documentId}/versions/{versionNumber}/{artifactName}`
- **隔离**：source 与 artifacts 逻辑隔离于不同 prefix
- **Fallback**：RustFS 不可用时不做本地文件系统 fallback，进入明确失败分支
- **迁移**：既有 `data/ingest` 本地历史文件不在首期自动迁移

### 调试产物（非对外契约）

- 无 XHTML/HTML 中间产物：Docling 直接产出 Markdown，不再经过 Tika→XHTML→清洗→MD 四跳管道
- `parse-result.json`：Docling 解析结果元数据（文档标题、作者、页数、语言、OCR 标识、解析引擎版本等）
- 不承诺长期稳定格式

### 处理参数基线

| 参数 | 值 | 说明 |
|------|------|------|
| chunking 引擎 | Docling HybridChunker | 布局感知 + token 预算控制，Java 侧不参与 chunking |
| max_tokens | 512 | 对齐 EMNLP 2024 最优 faithfulness (97.59) |
| merge_peers | true | 合并过小 chunk，等价滑动窗口效果 |
| 分块策略 | 结构感知、token 兜底 | Docling 原生 layout analysis，不切断表格/代码块 |
| 重试 | 瞬时错误最多 3 次 | 指数退避（1s/2s/4s + jitter） |
| 重处理 | 保留同一版本号，仅更新处理状态 | 与上传新版本/版本回退产生新版本号的语义不同 |
| 处理模式 | Virtual Threads + @Async + Semaphore | claim-then-submit，CAS 抢占后提交 Virtual Thread 执行 |
| 超时熔断 | 单文档 10min | 防止 PDF/OCR 长时间阻塞 |

### Docling 迁移计划

以下来自决策登记册 D11 + D22（2026-06-05），尚未落地到代码：

**范围**：Docling 替代 Tika 成为所有格式的唯一解析 + chunking 路径。Tika 及其依赖从项目中完全移除。

**解析路由（目标状态）**：

| 格式 | 解析引擎 | Chunking |
|------|---------|----------|
| PDF / DOCX / PPTX / XLSX | Docling Serve | Docling HybridChunker（server-side） |
| 图片 (PNG/JPG/TIFF) | Docling Serve（OCR） | Docling HybridChunker（server-side） |
| Markdown / HTML / TXT | Docling Serve | Docling HybridChunker（server-side） |
| CSV / EPUB / RTF 等 | 不支持（上传时拒绝） | — |

**实施步骤**：
1. `docker-compose.yml` 加 `docling-serve` 服务（含 health check 依赖链）
2. 新建 `DoclingDocumentParser`，注入 Arconia `DoclingServeApi`，带 `HybridChunkerOptions(max_tokens=512, merge_peers=true)`
3. `DocumentParserRouter` 简化为 `DOCLING` + `REJECT` 两条路由
4. `DocumentParseResult` 简化：移除 `rawXhtml`、`cleanedHtml` 字段
5. Java 侧 chunker（`MarkdownSegmenter` + `HeadingContextExtractor` + `ChunkWindowAssembler` ~260 行）完整移除
6. `SourceHint` → `ChunkMetadata` 域模型重构（headings 面包屑、pageNumber、contentType）
7. 双轨验证 1-2 天后删除 Tika + 所有相关文件 + pom.xml 依赖
8. 重建黄金样本基线

**净代码变化**：~150 LOC 新增（映射逻辑）、~500 LOC 删除（Tika + Java chunker）

**风险**：Docling Serve 不可用 = 解析完全阻塞，需 health check + fail-fast 启动校验

## 验证

### 自动化验证命令

| 命令 | 预期结果 | 失败处理 |
|------|----------|----------|
| `mvn test -pl . -Dtest="*IngestProcessing*"` | 处理链路测试通过 | 修复测试或代码 |
| `mvn test -pl . -Dtest="*DocumentContent*"` | 正文读取测试通过 | 修复测试或代码 |
| `mvn test -pl . -Dtest="*Storage*"` | 存储适配器测试通过 | 修复测试或代码 |

### 回归基线

黄金样本闭环（纯文字阶段已验证）：
`weak-pdf-001 → md-001 → md-002 → html-001 → word-001`

后续解析质量、清洗策略、chunk 质量的讨论应先将变更应用于此样本链，通过 `documents/chunks/preview` 和问答回归进行比对。

## 待定问题

- 正文读取的分页/分段能力何时引入？（当前仅 size cap + CONTENT_TOO_LARGE 拒绝）
- 图片理解、表格结构化节点、OCR 稳定支持的具体排期？
- 处理尝试表（processing attempt table）何时替代 document_version 中的 retry/reprocess 字段？
