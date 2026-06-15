---
title: Docling 解析引擎升级 + HybridChunker PRD
status: final
created: 2026-06-11
updated: 2026-06-11
supersedes: docs/archive/plans/ingest-cleaning/文档解析与清洗PRD.md
---

# Docling 解析引擎升级 + HybridChunker

## 0. 战略定位

> **我们赌：接受一个新容器依赖（Docling Serve + 网络跳），换来解析与 chunking 的统一管道，以及结构感知 metadata 的分水岭级提升。**
>
> 被放弃的是进程内 Tika 的零网络延迟和零外部依赖——那是一条已探到质量天花板的路径，继续投资不会让 RAG 下游获益更多。

## 1. 问题陈述

### 1.1 当前架构的局限

当前 ingest 管道的解析 + chunking 采用两条独立路径，存在三个根本问题：

**解析质量天花板。** Tika 对复杂格式（PDF、扫描件、带复杂布局的 DOCX）的解析质量有限。当前使用 Tika → XHTML → Jsoup 清洗 → flexmark 转 Markdown 的四跳管道，每次跳转都有信息损失。页眉页脚残留、标题标题粘连、表格结构退化、幽灵换行等问题在纯文字阶段已尽力优化，但 Tika 的布局分析能力是瓶颈。

**维护两条路径的成本。** 解析分两路（Tika 复杂格式 vs 原生文本直读），chunking 也分两路（Docling HybridChunker 复杂格式 vs Java 侧 StructuredFallbackDocumentChunker 原生格式），实际维护两套语义相近的逻辑。MarkdownSegmenter + HeadingContextExtractor + ChunkWindowAssembler 约 260 行 Java chunker 需要单独维护和测试。

**Metadata 质量不一致。** Java 侧启发式（正则）提取的标题上下文只有单一 heading 字符串，没有面包屑路径、没有页码、没有内容类型。PDF chunk 和 MD/HTML chunk 的 metadata 质量差异大，下游检索无法统一利用这些信号。

### 1.2 时机

决策登记册已明确 D11（Docling 替代 Tika）和 D22（Docling HybridChunker 接管所有格式的 chunking）的决策内容。Arconia BOM 0.27.1 已发布稳定版本，提供 `DoclingServeApi` 自动配置和 Testcontainers 开发服务支持。Docling 容器镜像已发布 quay.io，可直接集成到 docker-compose 编排中。

[ASSUMPTION: Docling 对 PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT 八种格式的输出质量 ≥ 当前 Tika + Java chunker 组合的输出质量。若其中某格式在迁移后黄金样本验证中明显退化，该格式回退到独立 PRD 处理，不阻塞整体迁移。]

## 2. 解决方案

### 2.1 总体方案

将所有格式的解析和 chunking 统一到 Docling Serve，Tika 及其全部依赖从项目中移除。

**解析路由（新）：**

| 格式 | 解析引擎 | Chunking |
|------|---------|----------|
| PDF | Docling Serve | Docling HybridChunker（server-side） |
| DOCX | Docling Serve | Docling HybridChunker（server-side） |
| PPTX | Docling Serve | Docling HybridChunker（server-side） |
| XLSX | Docling Serve | Docling HybridChunker（server-side） |
| 图片（PNG/JPG/TIFF） | Docling Serve（OCR） | Docling HybridChunker（server-side） |
| 原生 Markdown | Docling Serve | Docling HybridChunker（server-side） |
| 原生 HTML | Docling Serve | Docling HybridChunker（server-side） |
| TXT | Docling Serve | Docling HybridChunker（server-side） |
| CSV/EPUB/RTF 等 | 不支持（上传时拒绝） | — |

**架构变化：**

```
当前:                                新:
Source → Tika → XHTML                Source → Docling Serve（带 chunking options）
         → Jsoup 清洗                          → pre-chunked 结果 + metadata
         → flexmark 转 MD                       → 映射为 DocumentChunk → embedding
         → Java chunker → embedding
```

Java 侧不再有 chunking 逻辑，只负责配置参数（`HybridChunkerOptions`）和映射结果（Docling chunk JSON → `DocumentChunk` domain model）。

### 2.2 组件变化清单

**新增：**
- `DoclingDocumentParser` — 实现 `DocumentTextParser` 端口，注入 Arconia `DoclingServeApi`，接收 pre-chunked 结果
- `ChunkMetadata` 值对象 — 替代当前字符串 `SourceHint`，包含 `headings`（面包屑）、`pageNumber`、`contentType`
- `docling-serve` 容器服务定义 — docker-compose.yml 新增服务，含 health check 依赖链
- Maven 依赖：`arconia-docling-spring-boot-starter` + `arconia-dev-services-docling`（test scope）

**修改：**
- `DocumentParserRouter` — 删除 `TIKA` 路由，新增 `DOCLING` 和 `REJECT` 路由
- `DocumentParseResult` — 删除 `rawXhtml`、`cleanedHtml` 字段
- `ProcessingMetadataBuilder` — 适配 Docling 文档元数据（标题、作者、页数、语言、OCR 标识、解析引擎版本等）
- 黄金样本 — 重建 Docling 基线，旧样本删除

**删除：**
- `TikaDocumentTextParser`（~235 行）
- `TikaParseContextFactory`、`NoOpEmbeddedDocumentExtractor`
- `tika-core`、`tika-parsers-standard-package` Maven 依赖
- `StructuredFallbackDocumentChunker` + `MarkdownSegmenter` + `HeadingContextExtractor` + `ChunkWindowAssembler`（~260 行）
- `DocumentChunker` 端口（不再需要）

### 2.3 HybridChunker 参数

```java
ConvertDocumentOptions.builder()
    .doOcr(true)
    .includeImages(true)
    .chunkerOptions(new HybridChunkerOptions(
        512,           // max_tokens — 对齐论文最优 faithfulness 97.59
        true           // merge_peers — 合并过小块，等价滑动窗口效果
    ))
    .build();
```

`max_tokens` 和 `merge_peers` 从 `application.yaml` 读取，可配置热加载（重启生效）。

### 2.4 ChunkMetadata 域模型

```java
public record ChunkMetadata(
    List<String> headings,          // 面包屑标题链 ["Chapter 1", "Section 1.1"]
    int pageNumber,                 // 源页码（0 = 未知）
    ChunkContentType contentType    // PARAGRAPH | TABLE | LIST_ITEM | CODE_BLOCK | HEADING
) {}
```

替代当前 `SourceHint`（仅一个字符串 heading 字段），Metadata 信息全部来自 Docling 原生响应，不需要 Java 侧启发式推断。

### 2.5 中间产物调整

| 产物 | 当前 | 新 |
|------|------|----|
| `cleaned.md` | Tika → 清洗 → flexmark | Docling 直接产出 MD（质量更高） |
| `raw.xhtml` | 已有配置关闭，概念删除 | 删除 |
| `cleaned.html` | 已有配置关闭，概念删除 | 删除 |
| `parse-result.json` | Tika metadata | Docling 原始响应 JSON（含 layout 信息） |

存储接口 `DocumentProcessingArtifactStorage` 及其 Local/S3 实现不动，只有内容变了。

## 3 用户故事

> 注意：本 PRD 面向基础设施升级，终端用户无感。以下以开发者和维护者为视角。

1. 作为后端开发者，我希望所有格式的解析和 chunking 走统一路径，以便减少维护两套代码的认知负担。
2. 作为后端开发者，我希望 Tika 及其全部依赖能从项目中完全移除，以便减少构建体积和依赖安全风险。
3. 作为后端开发者，我希望 chunking 参数通过配置文件可调，以便按场景（demo/production）区分设置而不需要改代码。
4. 作为质量维护者，我希望 Java 侧 ~260 行的 chunker 能完整移除，以便减少测试维护量。
5. 作为质量维护者，我希望 ChunkMetadata 包含面包屑标题链、页码和内容类型，以便下游检索获得更丰富的结构信号。
6. 作为质量维护者，我希望旧黄金样本能用 Docling 基线重建，以便后续回归测试有准确基准。
7. 作为系统运维者，我希望 Docling Serve 在 docker-compose 中与现有基础设施一同编排，以便 `docker compose up -d` 一键启动全栈。
8. 作为系统运维者，我希望 Docling 服务不可用时能被 Actuator health 检测到，以便快速定位解析故障。

## 4 功能需求

| ID | 需求 | 优先级 | 依赖 |
|----|------|--------|------|
| FR-1 | docker-compose 新增 docling-serve 服务，含 health check 和依赖链 | P0 | — |
| FR-2 | 引入 Arconia Docling BOM + starter 依赖 | P0 | FR-1 |
| FR-3 | 新建 DoclingDocumentParser，调用 DoclingServeApi 接收 pre-chunked 结果 | P0 | FR-2 |
| FR-3a | FR-3 完成条件：全部 8 种格式可产出非空 `DocumentParseResult`；Docling 超时（≥30s）触发重试（最多 2 次）；Docling 4xx 映射为永久失败（`FAILED`），5xx/网络超时映射为瞬时错误（重试） | P0 | FR-3 |
| FR-4 | DocumentParserRouter 重构：TIKA → DOCLING + REJECT | P0 | FR-3 |
| FR-5 | DocumentParseResult 删除 rawXhtml / cleanedHtml 字段 | P0 | FR-3 |
| FR-6 | ChunkMetadata 值对象：headings 面包屑 + pageNumber + contentType | P0 | FR-3 |
| FR-7 | HybridChunker 参数从 application.yaml 读取（max_tokens / merge_peers） | P1 | FR-3 |
| FR-8 | 基本观测指标：docling.parse.duration / docling.parse.errors / docling.chunk.count | P1 | FR-3 |
| FR-9 | Tika 全部依赖和代码删除（TikaDocumentTextParser / pom.xml 依赖 / TikaParseContextFactory 等） | P0 | FR-3 |
| FR-10 | Java 侧 chunker 删除（StructuredFallbackDocumentChunker / MarkdownSegmenter / HeadingContextExtractor / ChunkWindowAssembler） | P0 | FR-3 |
| FR-11 | DocumentChunker 端口移除 | P0 | FR-10 |
| FR-12 | 黄金样本重建（Docling 基线，替换旧 Tika 基线） | P0 | FR-3 |
| FR-13 | Actuator health 暴露 Docling 连通性（Arconia 自动配置） | P0 | FR-2 |

## 5 成功指标

> 做完怎么算"值了"——不是上线就算，而是量化可验证。

| ID | 指标 | 目标值 | 测量方式 |
|----|------|--------|---------|
| SM-1 | 解析管道维护代码量 | 净删 ≥300 行 | `git diff --stat` 统计删除行数 |
| SM-2 | Chunk metadata 丰富度 | 全部 8 种格式的 chunk 均携带 `headings` 数组（非空时 ≥1 个元素） | `parse-result.json` 抽样验证 |
| SM-3 | 解析延迟退化 | 10 页文本 PDF 解析耗时 ≤ Tika 基线 + 2s（同一文档重复 10 次取中位数） | Micrometer `docling.parse.duration` |
| SM-4 | 文档处理成功率 | 迁移前后 7 日滚动窗口内 INDEXED 率（成功 / 总上传）不下降 | `/actuator/metrics/myai.ingest.process.success.total` |

## 6 非功能需求

| ID | 需求 | 指标 | 优先级 |
|----|------|------|--------|
| NFR-1 | 解析延迟：Docling 单文档解析（非 OCR）增加不超过 2s | ≤5s（典型 PDF） | P1 |
| NFR-2 | 存储占用：去除 XHTML/HTML 中间产物后减少约 30% artifact 存储 | 可观测 | P1 |
| NFR-3 | 启动依赖：Docling 不可用时应 fail-fast，不启动 ingest 相关组件 | Actuator health 检测 | P0 |

## 7 非目标范围

- 不引入图片理解或视觉问答（阶段性文档解析的视觉内容仍以 `[图片]` 占位保留）
- 不升级表格结构化节点（表格以 Markdown 形态保留，不做单元格级解析）
- 不引入父子分块或 richer node model
- 不升级 qa.ask 对外响应结构
- 不调整 vector metadata shape（除 ChunkMetadata 的字段升级外）
- 不修改 document / document version 主模型
- 不涉及 D3 Hybrid Search 检索策略调整
- 不涉及 D17 评估体系建设（黄金样本重建后评估基线另行 PRD）
- 不涉及 D1 Virtual Threads 并发模型改造

## 8 实施计划

### 阶段一：基础设施 + 依赖（半日）

1. docker-compose.yml 新增 `docling-serve` 服务，挂载模型卷，health check
2. pom.xml 引入 `arconia-bom` + `arconia-docling-spring-boot-starter` + `arconia-dev-services-docling`（test scope）
3. application.yaml 配置 `arconia.docling.*` 连接参数

### 阶段二：DoclingDocumentParser 实现（1 日）

1. 新建 `DoclingDocumentParser`，注入 `DoclingServeApi`
2. 配置 `ConvertDocumentOptions`（含 `HybridChunkerOptions`）
3. 实现文件内容 → Base64 → FileSource → 转换 → 映射 `DocumentParseResult`
4. 实现 ChunkMetadata 映射（headings / pageNumber / contentType）
5. 实现 processingMetadata 提取（Docling 文档元数据）

### 阶段三：路由重构 + 清理（半日）

1. `DocumentParserRouter` 重构
2. `DocumentParseResult` 字段简化
3. 删除 Tika 全部代码和依赖
4. 删除 Java 侧 chunker（StructuredFallbackDocumentChunker 等）
5. 删除 DocumentChunker 端口

### 阶段四：配置 + 观测 + 验收（半日）

1. HybridChunker 参数配置化
2. Micrometer 指标埋点
3. 旧黄金样本替换为 Docling 基线
4. 端到端验证：上传 → Docling 解析 → chunking → embedding → 入库

**总计估算：2.5 日**

## 9 验收标准

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | `docker compose up -d` 后 docling-serve 正常启动（含模型自动下载完毕），health check 通过 | `docker ps` + `curl localhost:5001/health`；首次启动等待 ≤ 5 分钟 |
| 2 | 上传 PDF 后状态可推进到 `INDEXED` | GET status → `INDEXED` |
| 3 | 上传 DOCX 后可正常解析并 chunk | chunk preview 可查看 |
| 4 | 上传图片（PNG/JPG/TIFF）后可正常 OCR + chunk | chunk preview 含 OCR 文本 |
| 5 | 上传原生 Markdown 后可正常解析 | chunk metadata 含 headings 面包屑 |
| 6 | Chunk metadata 正确：headings 面包屑、pageNumber、contentType | 数据库 chunk 表验证；TXT 格式的这三种字段允许全部为空 |
| 7 | CSV/EPUB/RTF 上传被正确拒绝，返回 `415 Unsupported Media Type` | 上传端到端测试，http status = 415，response body 含可读错误消息 |
| 8 | Tika 完整移除：代码中无 `tika` 引用 | `grep -r "tika\|org\.apache\.tika" src/ pom.xml` 无匹配 |
| 9 | Java chunker 完整移除：`StructuredFallbackDocumentChunker` 等不存在 | 类不存在 |
| 10 | SourceHint → ChunkMetadata 迁移：`DocumentChunk`、`DocumentChunkPreview`、`PgVectorDocumentVectorIndexer` 等全部引用方均已更新 | 编译通过 + 存量测试通过 |
| 11 | Docling 不可用时 Actuator `/health` 状态变为 `DOWN` | 停掉 docling-serve → 等待 health 刷新 → `/actuator/health` 返回 `DOWN` |
| 12 | chunking 参数从 application.yaml 读取，修改后重启生效 | 改 `max_tokens` → 重启 → chunk size 变化 |
| 13 | Docling 返回 4xx 时文档进入 `FAILED`（永久错误），5xx/网络超时时重试后进入 `FAILED` | 手动注入错误 → 验证文档终态和重试计数 |

## 10 风险与缓解

| 风险 | 影响 | 概率 | 缓解 |
|------|------|------|------|
| Docling Serve 不可用 → 全部解析阻塞 | 高 | 低 | docker-compose health check + Actuator health 暴露 + fail-fast（应用启动时若 Docling 不可达，`DoclingDocumentParser` 抛出明确异常拒绝 ingest 组件初始化） |
| Docling Serve 首次启动自动下载模型可能需数分钟 | 高 | 中 | `docker compose up --wait` 等待健康检查通过；docling-serve `startup-timeout` 设置为 10 分钟；README 新增首次启动说明 |
| Docling OCR 在 CPU 上过慢 | 中 | 中 | OCR 默认开启，可接受慢速；后续可通过配置关闭 |
| Arconia BOM 0.27.1 稳定性（< 1.0） | 低 | 低 | `DoclingDocumentParser` 是唯一使用点，API 变动影响可控 |
| 大文件 Base64 编码后超出 Docling API body size 限制 | 中 | 低 | 上传端已有 20MB 限制（现有校验）；Docling 默认无硬编码 body limit |
| 新黄金样本尚未建立 → 回归无基准 | 中 | 中 | 先建立 5 个基础样本（PDF/DOCX/MD/HTML/TXT），后续再扩充 |

## 11 专题风险

### 11.1 级联改动面

`DocumentParseResult.rawXhtml/cleanedHtml` 的删除和 `SourceHint → ChunkMetadata` 的替换涉及以下文件的同步更新。遗漏任何一处会导致编译失败。

**直接引用方（search confirmed）：**

| 类 | 引用字段 | 改动方向 |
|----|---------|---------|
| `DocumentParseResult` | `rawXhtml`, `cleanedHtml` | 删除两个字段，compact constructor 仅保留 `cleanedMarkdown` + `processingMetadata` |
| `TikaDocumentTextParser` | `rawXhtml`, `cleanedHtml` | 整个类删除 |
| `TextCleaningService` | `rawXhtml`, `cleanedHtml` | `cleanHtml` + `toMarkdown` 方法一并移除（仅保留 `cleanNativeMarkdown`，待评估） |
| `HtmlSemanticCleaner` | `rawXhtml`, `cleanedHtml` | 若 TextCleaningService 需删除则一并删除 |
| `HtmlToMarkdownRenderer` | `rawXhtml`, `cleanedHtml` | 同上 |
| `SourceHint` | `SourceHint` | 整个类删除，替换为 `ChunkMetadata` |
| `DocumentChunk` | `SourceHint` | 字段类型从 `SourceHint` 改为 `ChunkMetadata` |
| `DocumentChunkPreview` | `SourceHint` | 同上 |
| `ChunkWindowAssembler` | `SourceHint` | 整个类删除 |
| `PgVectorDocumentVectorIndexer` | `SourceHint` | 更新 metadata 写入逻辑 |
| `StructuredFallbackDocumentChunker` | `SourceHint` | 整个类删除 |
| `IngestCleaningGoldenSamplesTest` | `rawXhtml`, `SourceHint` | 重写为 Docling 基线 |
| `TikaDocumentTextParserTest` | 全部 | 删除 |
| `StructuredFallbackDocumentChunkerTest` | 全部 | 删除 |
| `SourceHintTest` | 全部 | 删除，替换为 `ChunkMetadataTest` |

**应对方法**：阶段三执行前先在 IDE 中全局搜索 `rawXhtml`、`cleanedHtml`、`SourceHint`、`TIKA`，生成完整引用清单，逐文件处理。阶段三结束后再搜一次确认零残留。

### 11.2 TextCleaningService 去留

Docling 产出的是格式化良好的 Markdown，不再需要 Jsoup HTML 清洗 + flexmark 转换。但 `TextCleaningService` 中的 `cleanNativeMarkdown` 对 Docling 产出的 MD 可能仍有价值（统一换行符、去除控制字符、压缩空行）。建议：阶段二先保留 `cleanNativeMarkdown` 的最小调用，阶段三根据 Docling 输出质量决定是否移除。

### 11.3 TXT 的 ChunkMetadata 边界

Docling 对纯文本文件能产出 chunk，但 `headings`、`pageNumber`、`contentType` 三个字段将全部为空。需要：
- `ChunkMetadata` 的 `headings` 定义为 `List<String>`（空 list 合法），`pageNumber` 默认 0，`contentType` 默认 `PARAGRAPH`
- 下游代码不能对这三个字段做非空断言
- 验收标准已补充此约束

## 12 待确定事项
