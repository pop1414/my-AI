---
title: Docling 解析引擎升级 + HybridChunker
status: final
created: 2026-06-11
updated: 2026-06-11
supersedes: docs/archive/plans/ingest-cleaning/文档解析与清洗PRD.md
---

# PRD: Docling 解析引擎升级 + HybridChunker

## 0. Document Purpose

本 PRD 面向 my-AI 项目的后端开发者、质量维护者和系统运维者，描述 ingest 子域中文档解析引擎从 Tika 到 Docling Serve 的完整迁移方案。文档按 Glossary 锚定术语、Features 分组嵌套 FR、Assumptions 索引汇总的方式组织。本 PRD 构建于决策登记册 D11（Docling 替代 Tika）和 D22（HybridChunker 接管所有格式 chunking）之上，不重复其决策论证。

## 1. Vision

my-AI 的 ingest 管道当前采用 Tika 解析 + Java 侧 chunking 的双路径架构，存在解析质量天花板、维护两套逻辑的冗余成本、以及 chunk metadata 质量不一致三个根本问题。Tika 的布局分析能力已探到极限——四跳管道（Tika → XHTML → Jsoup → flexmark → Markdown）每次跳转都有信息损失，而 Java 侧 ~260 行的启发式 chunker 仅产出单一 heading 字符串，无法为下游检索提供结构信号。

本次升级将所有格式的解析和 chunking 统一到 Docling Serve——一个具备布局感知能力和 server-side HybridChunking 的容器化服务。Java 侧不再持有 chunking 逻辑，只负责配置参数和映射结果。Tika 及其全部依赖从项目中移除。

接受一个新容器依赖（Docling Serve + 网络跳），换来解析与 chunking 的统一管道、结构感知 metadata 的分水岭级提升、以及净删 300+ 行维护代码。被放弃的是进程内 Tika 的零网络延迟和零外部依赖——那是一条已探到质量天花板的路径，继续投资不会让 RAG 下游获益更多。

## 2. Target User

### 2.1 Jobs To Be Done

- 作为后端开发者，我希望所有格式的解析和 chunking 走统一路径，减少维护两套代码的认知负担。
- 作为后端开发者，我希望 Tika 及其全部依赖能从项目中完全移除，减少构建体积和依赖安全风险。
- 作为后端开发者，我希望 chunking 参数通过配置文件可调，按场景（demo/production）区分设置而不改代码。
- 作为质量维护者，我希望 chunk metadata 包含面包屑标题链、页码和内容类型，下游检索获得更丰富的结构信号。
- 作为质量维护者，我希望旧黄金样本能用 Docling 基线重建，后续回归测试有准确基准。
- 作为系统运维者，我希望 Docling Serve 在 docker-compose 中与现有基础设施一同编排，`docker compose up -d` 一键启动全栈。
- 作为系统运维者，我希望 Docling 服务不可用时能被 Actuator health 检测到，快速定位解析故障。

### 2.2 Key User Journeys

> 注意：本 PRD 面向基础设施升级，终端用户无感。以下以开发者和维护者为视角，采用轻量级描述。

- **UJ-1. 开发者上传文档后自动走 Docling 管道。**
  - **Persona + context：** 后端开发者，在本地环境验证 ingest 端到端流程。
  - **Entry state：** `docker compose up -d` 完成，Docling Serve 健康。
  - **Path：** 上传 PDF → DocumentParserRouter 路由到 DOCLING → DoclingDocumentParser 调用 DoclingServeApi.convertSource 产出 cleanedMarkdown → DocumentChunker（DoclingDocumentChunker）调用 HybridChunker 分块 → embedding → 入库。
  - **Climax：** GET document status 返回 `INDEXED`，chunk preview 显示带 headings 面包屑的分块内容。
  - **Resolution：** 文档可用于 RAG 问答。
  - **Edge case：** Docling 返回 4xx → 文档进入 `FAILED`（永久错误）；5xx/网络超时 → 重试最多 2 次后进入 `FAILED`。

- **UJ-2. 运维者排查 Docling 不可用导致的解析故障。**
  - **Persona + context：** 系统运维者，生产环境监控。
  - **Entry state：** 用户反馈文档上传后一直 processing。
  - **Path：** 检查 `/actuator/health` → Docling 状态 `DOWN` → 定位到 docling-serve 容器异常 → 重启容器。
  - **Climax：** health 恢复 `UP`，队列中文档自动重新处理。
  - **Resolution：** 系统恢复正常。

- **UJ-3. 质量维护者重建黄金样本基线。**
  - **Persona + context：** 质量维护者，迁移后需要新的回归基准。
  - **Entry state：** DoclingDocumentParser 实现完成。
  - **Path：** 准备 5 个基础样本（PDF/DOCX/MD/HTML/TXT）→ 运行解析 → 审查输出质量 → 保存为新黄金样本。
  - **Climax：** 所有样本的 chunk 和 metadata 质量满足预期。
  - **Resolution：** 旧 Tika 基线删除，新基线可用于后续回归测试。

## 3. Glossary

- **Docling Serve** — IBM 开源的文档解析容器服务，具备布局感知能力和 OCR 支持，通过 HTTP API 接收文件并返回结构化结果。
- **HybridChunker** — Docling Serve 内置的 server-side 分块器，结合规则和语义进行文档分块。迁移后由 DoclingDocumentChunker 独立调用（分块职责），与 DoclingDocumentParser（转换职责）分离。
- **HybridChunkerOptions** — HybridChunker 的配置参数，包括 `max_tokens`（单块最大 token 数）和 `merge_peers`（是否合并过小块）。
- **Arconia BOM** — Spring Boot 生态的 Docling 集成框架，提供 `DoclingServeApi` 自动配置和 Testcontainers 开发服务支持。
- **ChunkMetadata** — 新增的域模型值对象，替代 SourceHint，包含 `headings`（面包屑标题链）、`pageNumber`（源页码）、`contentType`（内容类型枚举）。
- **ChunkContentType** — 内容类型枚举：PARAGRAPH、TABLE、LIST_ITEM、CODE_BLOCK、HEADING。
- **SourceHint** — 当前域模型，仅包含单一 `heading` 字符串，迁移后删除。
- **DocumentTextParser** — 端口接口（port），定义文件内容 → DocumentParseResult 的契约，迁移后由 DoclingDocumentParser 实现。
- **DocumentParseResult** — 解析结果域模型，迁移后仅保留 `cleanedMarkdown` + `processingMetadata`，删除 `rawXhtml` 和 `cleanedHtml`。
- **DocumentChunker** — 端口接口（port），定义 Markdown → List<DocumentChunk> 的分块契约。保留作为转换与分块的架构边界，迁移后由 DoclingDocumentChunker 实现（调用 Docling HybridChunker）。
- **DocumentParserRouter** — 解析路由组件，根据文件类型选择解析引擎（迁移后为 DOCLING 或 REJECT）。
- **Tika** — Apache 开源的文档解析库，当前使用的进程内解析引擎，迁移后完全移除。

## 4. Features

> **FR 全局索引** — FR 编号跨特性全局唯一，按特性分组呈现。依赖关系见下表。
>
> | FR | 特性 | 简述 | 依赖 | SM 覆盖 |
> |----|------|------|------|---------|
> | FR-1 | 4.1 | Docling Serve 容器编排 | — | SM-4（隐式：容器可用是全流程前提） |
> | FR-2 | 4.1 | Arconia Docling 依赖引入 | FR-1 | SM-3（隐式：依赖安装是解析前提） |
> | FR-13 | 4.1 | Actuator Health 暴露 | FR-1 | SM-4（隐式：health 检测是运维可观测前提） |
> | FR-3 | 4.2 | DoclingDocumentParser 实现 | FR-2 | SM-3, SM-4 |
> | FR-4 | 4.3 | DocumentParserRouter 重构 | FR-3 | SM-4 |
> | FR-5 | 4.3 | DocumentParseResult 字段简化 | FR-3 | SM-1（代码删减的一部分） |
> | FR-6 | 4.3 | ChunkMetadata 值对象 | FR-3 | SM-2 |
> | FR-9 | 4.4 | Tika 全量移除 | FR-4 | SM-1 |
> | FR-10 | 4.4 | Java 侧 Chunker 全量移除 | FR-4 | SM-1 |
> | FR-11 | 4.4 | DocumentChunker 端口保留，实现切换为 DoclingDocumentChunker | FR-10 | SM-1 |
> | FR-7 | 4.5 | HybridChunker 参数配置化 | FR-3 | SM-3（隐式：参数影响分块质量） |
> | FR-8 | 4.5 | 观测指标埋点 | FR-3 | —（可观测性基础设施，无量化 SM） |
> | FR-12 | 4.5 | 黄金样本重建 | FR-3 | SM-4 开发期等价验证 |

### 4.1 Docling Serve 基础设施

**Description：** 在现有 docker compose 编排中新增 Docling Serve 容器服务，并引入 Arconia BOM 依赖。Docling Serve 通过 health check 与 Spring Boot 应用形成启动依赖链。Actuator 自动暴露 Docling 连通性状态。Realizes UJ-2。

**Functional Requirements:**

#### FR-1: Docling Serve 容器编排

docker-compose.yml 新增 `docling-serve` 服务，含 health check 和启动依赖链。

**Consequences (testable):**
- `docker compose up -d` 后 docling-serve 正常启动（含模型自动下载完毕），health check 通过。
- 首次启动等待 ≤ 5 分钟。
- Docling Serve 不可用时 Spring Boot 应用的 `/actuator/health` 状态变为 `DOWN`。

#### FR-2: Arconia Docling 依赖引入

引入 Arconia Docling BOM + starter 依赖（`arconia-docling-spring-boot-starter` + `arconia-dev-services-docling` test scope）。

**Consequences (testable):**
- pom.xml 包含 `arconia-bom`、`arconia-docling-spring-boot-starter`、`arconia-dev-services-docling`（test scope）。
- `application.yaml` 配置 `arconia.docling.*` 连接参数，应用可连接 Docling Serve。

#### FR-13: Actuator Health 暴露 Docling 连通性

Actuator health 自动暴露 Docling 连通性（Arconia 自动配置）。

**Consequences (testable):**
- 停掉 docling-serve → 等待 health 刷新 → `/actuator/health` 返回 `DOWN`。
- 重启 docling-serve → `/actuator/health` 恢复 `UP`。

### 4.2 DoclingDocumentParser 统一解析

**Description：** 新建 DoclingDocumentParser 实现 DocumentTextParser 端口，注入 Arconia DoclingServeApi。所有 8 种支持格式（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）通过统一路径调用 DoclingServeApi.convertSource 进行纯 Markdown 转换，产出 DocumentParseResult。转换与分块职责分离：DoclingDocumentParser 只负责转换，分块由 DoclingDocumentChunker 通过 DocumentChunker 端口独立完成。Docling 超时触发重试，4xx 映射为永久失败，5xx/网络超时映射为瞬时错误。Realizes UJ-1。

[ASSUMPTION: Docling 对 PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT 八种格式的输出质量 ≥ 当前 Tika + Java chunker 组合的输出质量。若其中某格式在迁移后黄金样本验证中明显退化，该格式回退到独立 PRD 处理，不阻塞整体迁移。]

[ASSUMPTION: Arconia BOM 0.27.1 与 Spring Boot 3.5.8、Spring AI 1.1.2、Java 21 兼容。若不兼容，评估升级到兼容的 Arconia 版本或直接实现 DoclingServeApi。]

**Functional Requirements:**

#### FR-3: DoclingDocumentParser 实现

新建 `DoclingDocumentParser`，调用 DoclingServeApi.convertSource 进行纯 Markdown 转换（不包含分块），全部 8 种格式可产出非空 `DocumentParseResult`。分块由 DoclingDocumentChunker 通过 DocumentChunker 端口独立完成。

**Consequences (testable):**
- 上传 PDF 后状态可推进到 `INDEXED`。
- 上传 DOCX 后可正常解析并 chunk，chunk preview 可查看。
- 上传图片（PNG/JPG/TIFF）后可正常 OCR + chunk，chunk preview 含 OCR 文本。
- 上传原生 Markdown 后可正常解析，chunk metadata 含 headings 面包屑。
- Docling 超时（≥30s）触发重试，最多 2 次。
- Docling 返回 4xx 时文档进入 `FAILED`（永久错误）。
- Docling 返回 5xx/网络超时时重试后进入 `FAILED`。
- TXT 格式的 headings、pageNumber、contentType 三个字段允许全部为空（headings 为空 list，pageNumber 默认 0，contentType 默认 PARAGRAPH）。

**Out of Scope:**
- CSV/EPUB/RTF 等不支持格式——由 FR-4 的 REJECT 路由处理。

### 4.3 解析路由与域模型重构

**Description：** 重构 DocumentParserRouter 删除 TIKA 路由，新增 DOCLING 和 REJECT 路由。简化 DocumentParseResult 删除 rawXhtml/cleanedHtml 字段。新建 ChunkMetadata 值对象替代 SourceHint，包含 headings 面包屑、pageNumber、contentType 三个结构化字段。存储接口 `DocumentProcessingArtifactStorage` 及其 Local/S3 实现不变，仅内容变了。Realizes UJ-1。

**Functional Requirements:**

#### FR-4: DocumentParserRouter 重构

DocumentParserRouter 删除 `TIKA` 路由，新增 `DOCLING` 和 `REJECT` 路由。CSV/EPUB/RTF 等不支持格式上传时被拒绝。

**Consequences (testable):**
- CSV/EPUB/RTF 上传返回 `415 Unsupported Media Type`，response body 含可读错误消息。
- 所有支持格式路由到 DoclingDocumentParser。

#### FR-5: DocumentParseResult 字段简化

DocumentParseResult 删除 `rawXhtml`、`cleanedHtml` 字段，compact constructor 仅保留 `cleanedMarkdown` + `processingMetadata`。

**Consequences (testable):**
- 编译通过，DocumentParseResult 不再包含 rawXhtml 和 cleanedHtml 字段。

#### FR-6: ChunkMetadata 值对象

新建 `ChunkMetadata` 值对象替代 `SourceHint`，包含 `headings`（`List<String>` 面包屑标题链）、`pageNumber`（`int`，0 = 未知）、`contentType`（`ChunkContentType` 枚举）。

**Consequences (testable):**
- Chunk metadata 正确：headings 面包屑、pageNumber、contentType 均来自 Docling 原生响应。
- SourceHint → ChunkMetadata 迁移：`DocumentChunk`、`DocumentChunkPreview`、`PgVectorDocumentVectorIndexer` 等全部引用方均已更新，编译通过 + 存量测试通过。

### 4.4 遗留代码清理

**Description：** 完全移除 Tika 及其全部依赖（代码 + Maven + 配置），删除 Java 侧 ~260 行 chunker 代码（StructuredFallbackDocumentChunker、MarkdownSegmenter、HeadingContextExtractor、ChunkWindowAssembler），替换为 DoclingDocumentChunker（调用 Docling HybridChunker）。DocumentChunker 端口保留作为转换与分块的架构边界。

**Functional Requirements:**

#### FR-9: Tika 全量移除

Tika 全部依赖和代码删除，包括 `TikaDocumentTextParser`（~235 行）、`TikaParseContextFactory`、`NoOpEmbeddedDocumentExtractor`、pom.xml 中的 `tika-core` 和 `tika-parsers-standard-package` 依赖。

**Consequences (testable):**
- `grep -r "tika\|org\.apache\.tika" src/ pom.xml` 无匹配。
- `TikaDocumentTextParser`、`TikaParseContextFactory`、`NoOpEmbeddedDocumentExtractor` 类不存在。

**Feature-specific NFRs:**
- TextCleaningService 中的 `cleanNativeMarkdown` 先保留最小调用（统一换行符、去除控制字符、压缩空行），根据 Docling 输出质量再决定是否移除。

#### FR-10: Java 侧 Chunker 全量移除，替换为 DoclingDocumentChunker

删除 `StructuredFallbackDocumentChunker`、`MarkdownSegmenter`、`HeadingContextExtractor`、`ChunkWindowAssembler`（合计 ~260 行），新建 `DoclingDocumentChunker` 实现 `DocumentChunker` 端口（调用 DoclingServeApi.chunkSourceWithHybridChunker 进行分块）。

**Consequences (testable):**
- `StructuredFallbackDocumentChunker`、`MarkdownSegmenter`、`HeadingContextExtractor`、`ChunkWindowAssembler` 类不存在。
- 相关测试类（`StructuredFallbackDocumentChunkerTest`）已删除。
- `DoclingDocumentChunker` 存在且实现 `DocumentChunker` 端口。
- `ProcessDocumentApplicationService` 注入 `DocumentChunker` 无需修改（端口接口不变）。

#### FR-11: DocumentChunker 端口保留，实现切换

保留 `DocumentChunker` 端口接口作为转换与分块的架构边界，实现从 `StructuredFallbackDocumentChunker` 切换为 `DoclingDocumentChunker`。

**Consequences (testable):**
- `DocumentChunker` 端口接口仍存在且被 `ProcessDocumentApplicationService` 注入。
- `DoclingDocumentChunker` 是 `DocumentChunker` 的唯一实现。
- `StructuredFallbackDocumentChunker` 类不存在（已删除）。

### 4.5 可观测性与配置化

**Description：** HybridChunker 参数（max_tokens、merge_peers）从 application.yaml 读取，可配置热加载（重启生效）。新增 Micrometer 指标埋点。Realizes UJ-3。

**Functional Requirements:**

#### FR-7: HybridChunker 参数配置化

`max_tokens` 和 `merge_peers` 从 `application.yaml` 读取，修改后重启生效。

**Consequences (testable):**
- 改 `max_tokens` → 重启 → chunk size 变化。
- 默认值：`max_tokens = 512`（对齐论文最优 faithfulness 97.59），`merge_peers = true`。

#### FR-8: 观测指标埋点

新增 Micrometer 指标：`docling.parse.duration`（解析耗时）、`docling.parse.errors`（解析错误计数）、`docling.chunk.count`（分块数量）。

**Consequences (testable):**
- 上传文档后 `/actuator/metrics` 可查询 `docling.parse.duration`、`docling.parse.errors`、`docling.chunk.count`。

#### FR-12: 黄金样本重建

旧 Tika 基线黄金样本删除，以 Docling 为基线重建。先建立 5 个基础样本（PDF/DOCX/MD/HTML/TXT），后续再扩充。Realizes UJ-3。

**Consequences (testable):**
- `IngestCleaningGoldenSamplesTest` 重写为 Docling 基线，测试通过。
- 旧 Tika 基线样本文件已删除。

## 5. Non-Goals (Explicit)

- 不引入图片理解或视觉问答（阶段性文档解析的视觉内容仍以 `[图片]` 占位保留）
- 不升级表格结构化节点（表格以 Markdown 形态保留，不做单元格级解析）
- 不引入父子分块或 richer node model
- 不升级 qa.ask 对外响应结构
- 不调整 vector metadata shape（除 ChunkMetadata 的字段升级外）
- 不修改 document / document version 主模型
- 不涉及 D3 Hybrid Search 检索策略调整
- 不涉及 D17 评估体系建设（黄金样本重建后评估基线另行 PRD）
- 不涉及 D1 Virtual Threads 并发模型改造

## 6. MVP Scope

### 6.1 In Scope

- Docling Serve 容器编排 + health check 依赖链
- Arconia BOM + starter 依赖引入
- DoclingDocumentParser 实现（8 种格式统一解析）
- DocumentParserRouter 重构（DOCLING + REJECT）
- DocumentParseResult 字段简化（删除 rawXhtml/cleanedHtml）
- ChunkMetadata 值对象替代 SourceHint
- Tika 全量移除（代码 + 依赖 + 配置）
- Java 侧 chunker 全量移除（~260 行）+ DoclingDocumentChunker 替换实现
- HybridChunker 参数配置化
- Micrometer 观测指标
- 黄金样本重建（5 个基础样本）

### 6.2 Out of Scope for MVP

- TextCleaningService 完全移除 — 先保留 `cleanNativeMarkdown`，根据 Docling 输出质量后续决策
- 黄金样本扩充（PDF/DOCX/MD/HTML/TXT 之外的格式） — 后续迭代
- 评估体系建设（自动化评估管线） — 另行 D17 PRD

## 7. Success Metrics

**Primary**
- **SM-1：** 解析管道维护代码量 — 净删 ≥300 行。验证方式：`git diff --stat` 统计删除行数。Validates FR-9, FR-10。*注：FR-11 为端口保留+实现切换，不计入净删统计。*
- **SM-2：** Chunk metadata 丰富度 — 全部 8 种格式的 chunk 均携带 `headings` 数组（非空时 ≥1 个元素）。验证方式：`parse-result.json` 抽样验证。Validates FR-6。
- **SM-3：** 解析延迟退化 — 10 页文本 PDF 解析耗时 ≤ Tika 基线 + 2s（同一文档重复 10 次取中位数）。验证方式：Micrometer `docling.parse.duration`。Validates FR-3。
- **SM-4：** 文档处理成功率（部署后监控指标） — 迁移前后 7 日滚动窗口内 INDEXED 率（成功 / 总上传）不下降。验证方式：`/actuator/metrics/myai.ingest.process.success.total`。Validates FR-3, FR-4。*开发期等价验证：FR-12 的 5 个黄金样本全部解析为 INDEXED。*

**Counter-metrics (do not optimize)**
- **SM-C1：** 解析延迟绝对值 — 不追求比 Tika 更快（Docling 的布局分析和 OCR 需要更多时间是可接受的），只监控退化幅度不超过 SM-3 阈值。

## 8. Open Questions

1. TextCleaningService 中的 `cleanNativeMarkdown` 是否在 Docling 迁移后仍有价值？需阶段二根据 Docling 输出质量评估。[详见 §6.2 Out of Scope 首条 + Risk and Mitigations 级联改动面中 TextCleaningService 行]

## 9. Assumptions Index

- §4.2 [ASSUMPTION: Docling 对 PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT 八种格式的输出质量 ≥ 当前 Tika + Java chunker 组合的输出质量。若其中某格式在迁移后黄金样本验证中明显退化，该格式回退到独立 PRD 处理，不阻塞整体迁移。]
- §4.2 [ASSUMPTION: Arconia BOM 0.27.1 与 Spring Boot 3.5.8、Spring AI 1.1.2、Java 21 兼容。若不兼容，评估升级到兼容的 Arconia 版本或直接实现 DoclingServeApi。]

---

## Cross-Cutting NFRs

| ID | 需求 | 指标 | 优先级 |
|----|------|------|--------|
| NFR-1 | 解析延迟：Docling 单文档解析（非 OCR）增加不超过 2s | ≤5s（典型 PDF） | P1 |
| NFR-2 | 存储占用：去除 XHTML/HTML 中间产物后减少 artifact 存储 | 迁移前后同一 10 页 PDF 的 artifact 存储对比，减少 ≥20% | P1 |
| NFR-3 | 启动依赖：Docling 不可用时应 fail-fast，不启动 ingest 相关组件 | Actuator health 检测；fail-fast 适用于 startup-timeout 超期后，首次启动（含模型下载）豁免 | P0 |

## Risk and Mitigations

| 风险 | 影响 | 概率 | 缓解 |
|------|------|------|------|
| Docling Serve 不可用 → 全部解析阻塞 | 高 | 低 | docker compose health check + Actuator health 暴露 + fail-fast（应用启动时若 Docling 不可达，DoclingDocumentParser 抛出明确异常拒绝 ingest 组件初始化） |
| Docling Serve 首次启动自动下载模型可能需数分钟 | 高 | 中 | `docker compose up --wait` 等待健康检查通过；docling-serve `startup-timeout` 设置为 10 分钟；README 新增首次启动说明 |
| Docling OCR 在 CPU 上过慢 | 中 | 中 | OCR 默认开启，可接受慢速；后续可通过配置关闭 |
| Arconia BOM 0.27.1 稳定性（< 1.0） | 低 | 低 | DoclingDocumentParser 和 DoclingDocumentChunker 是仅有的两个使用点，API 变动影响可控 |
| 大文件 Base64 编码后超出 Docling API body size 限制 | 中 | 低 | 上传端已有 20MB 限制（现有校验）；Docling 默认无硬编码 body limit |
| 新黄金样本尚未建立 → 回归无基准 | 中 | 中 | 先建立 5 个基础样本（PDF/DOCX/MD/HTML/TXT），后续再扩充 |

### 级联改动面

`DocumentParseResult.rawXhtml/cleanedHtml` 的删除和 `SourceHint → ChunkMetadata` 的替换涉及以下文件的同步更新。遗漏任何一处会导致编译失败。

**直接引用方（search confirmed）：**

| 类 | 引用字段 | 改动方向 |
|----|---------|---------|
| `DocumentParseResult` | `rawXhtml`, `cleanedHtml` | 删除两个字段，compact constructor 仅保留 `cleanedMarkdown` + `processingMetadata` |
| `TikaDocumentTextParser` | `rawXhtml`, `cleanedHtml` | 整个类删除 |
| `TextCleaningService` | `rawXhtml`, `cleanedHtml` | `cleanHtml` + `toMarkdown` 方法一并移除（仅保留 `cleanNativeMarkdown`，待评估） |
| `HtmlSemanticCleaner` | `rawXhtml`, `cleanedHtml` | 若 TextCleaningService 需删除则一并删除 |
| `HtmlToMarkdownRenderer` | `rawXhtml`, `cleanedHtml` | 同上 |
| `SourceHint` | `SourceHint` | 整个类删除，替换为 ChunkMetadata |
| `DocumentChunk` | `SourceHint` | 字段类型从 SourceHint 改为 ChunkMetadata |
| `DocumentChunkPreview` | `SourceHint` | 同上 |
| `ChunkWindowAssembler` | `SourceHint` | 整个类删除 |
| `PgVectorDocumentVectorIndexer` | `SourceHint` | 更新 metadata 写入逻辑 |
| `StructuredFallbackDocumentChunker` | `SourceHint` | 整个类删除 |
| `IngestCleaningGoldenSamplesTest` | `rawXhtml`, `SourceHint` | 重写为 Docling 基线 |
| `TikaDocumentTextParserTest` | 全部 | 删除 |
| `StructuredFallbackDocumentChunkerTest` | 全部 | 删除 |
| `SourceHintTest` | 全部 | 删除，替换为 `ChunkMetadataTest` |

**应对方法**：执行前先在 IDE 中全局搜索 `rawXhtml`、`cleanedHtml`、`SourceHint`、`TIKA`，生成完整引用清单，逐文件处理。清理结束后再搜一次确认零残留。

### TXT 的 ChunkMetadata 边界

Docling 对纯文本文件能产出 chunk，但 `headings`、`pageNumber`、`contentType` 三个字段将全部为空。需要：
- `ChunkMetadata` 的 `headings` 定义为 `List<String>`（空 list 合法），`pageNumber` 默认 0，`contentType` 默认 PARAGRAPH
- 下游代码不能对这三个字段做非空断言
- FR-3 Consequences 已补充此约束

## Rollout and Change Management

### 实施计划

**阶段一：基础设施 + 依赖（半日）**
1. docker-compose.yml 新增 `docling-serve` 服务，挂载模型卷，health check
2. pom.xml 引入 `arconia-bom` + `arconia-docling-spring-boot-starter` + `arconia-dev-services-docling`（test scope）
3. application.yaml 配置 `arconia.docling.*` 连接参数

**阶段二：DoclingDocumentParser 实现（1 日）**
1. 新建 DoclingDocumentParser，注入 DoclingServeApi
2. 配置 ConvertDocumentOptions（仅 OutputFormat.MARKDOWN）
3. 调用 DoclingServeApi.convertSource（纯转换端点，不使用 HybridChunker）
4. 实现文件内容 → Base64 → FileSource → convertSource → 映射 DocumentParseResult
5. 实现 processingMetadata 提取（Docling 文档元数据）

**阶段三：路由重构 + 清理（半日）**
1. DocumentParserRouter 重构
2. DocumentParseResult 字段简化
3. 删除 Tika 全部代码和依赖
4. 删除 Java 侧 chunker（StructuredFallbackDocumentChunker 等），新建 DoclingDocumentChunker 实现 DocumentChunker 端口

**阶段四：配置 + 观测 + 验收（半日）**
1. HybridChunker 参数配置化
2. Micrometer 指标埋点
3. 旧黄金样本替换为 Docling 基线
4. 端到端验证：上传 → Docling 解析 → chunking → embedding → 入库

**总计估算：2.5 日**
